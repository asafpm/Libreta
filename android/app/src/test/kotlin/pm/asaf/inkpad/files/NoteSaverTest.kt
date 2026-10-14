// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.files

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.inkpad.NoteState
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.PointList
import pm.asaf.inkpad.core.model.StrokeTool
import pm.asaf.inkpad.library.FakeDocumentsProvider
import pm.asaf.inkpad.library.FolderListing
import pm.asaf.inkpad.library.FolderRef
import pm.asaf.inkpad.library.LibraryEntry
import java.io.File
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteSaverTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testData = File(checkNotNull(System.getProperty("inkpad.testdata")))
    private val saver = NoteSaver(context)
    private lateinit var directory: File
    private lateinit var root: FolderRef

    private val stroke =
        Element.Stroke(
            StrokeTool.PEN,
            Color.of(237, 83, 83),
            2.26,
            PointList.of(100.0, 100.0, 200.0, 150.0, 300.0, 100.0),
        )

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        root = FolderRef.rootOf(FakeDocumentsProvider.treeUri(), "Notes")
    }

    /** Opens testdata [sample] copied into the library as [name]. */
    private fun open(
        sample: String,
        name: String,
    ): NoteState.Loaded {
        File(testData, sample).copyTo(File(directory, name))
        val entry =
            FolderListing.list(context.contentResolver, root).filterIsInstance<LibraryEntry.Document>().single {
                it.name ==
                    name
            }
        return NoteLoader.load(context, entry.uri, name, DocumentLocation(listOf(root), name)) as NoteState.Loaded
    }

    private fun NoteState.Loaded.write() {
        val page = edits.document.pages[0]
        edits.pageEdited(0, page.withElementAdded(stroke), version = 1)
    }

    private fun lastElement(name: String): Element =
        read(name)
            .document.pages[0]
            .layers
            .last()
            .elements
            .last()

    private fun read(name: String) = File(directory, name).source().buffer().use { XoppReader.read(it) }

    @Test
    fun savesTheNoteInPlace() {
        val note = open("note-taking.xopp", "Lecture.xopp")
        val before =
            note.document.pages[0]
                .layers
                .last()
                .elements.size
        note.write()
        assertEquals(SaveResult.Saved, saver.save(note.file, note.edits, note.files))
        assertFalse(note.edits.dirty)

        val saved = read("Lecture.xopp")
        val elements =
            saved.document.pages[0]
                .layers
                .last()
                .elements
        assertEquals(before + 1, elements.size)
        assertEquals(stroke, elements.last())
        // Everything else is as it was.
        assertEquals(note.document.pages.drop(1), saved.document.pages.drop(1))
        assertEquals("Inkpad 0.1.0", saved.document.creator)
        // A new Xournal++-sized preview: the longer side 128 px.
        val preview = checkNotNull(saved.document.preview).toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(preview, 0, preview.size)
        assertEquals(128, maxOf(bitmap.width, bitmap.height))
        // Saving again without changes does nothing.
        val modified = File(directory, "Lecture.xopp").lastModified()
        assertEquals(SaveResult.Unchanged, saver.save(note.file, note.edits, note.files))
        assertEquals(modified, File(directory, "Lecture.xopp").lastModified())
    }

    @Test
    fun aFileChangedElsewhereIsNotOverwritten() {
        val note = open("note-taking.xopp", "Lecture.xopp")
        note.write()
        // E.g. a sync app replaced it meanwhile.
        val elsewhere = File(directory, "Lecture.xopp")
        File(testData, "physics.xopp").copyTo(elsewhere, overwrite = true)
        elsewhere.setLastModified(elsewhere.lastModified() + 60_000)

        val result = assertIs<SaveResult.SavedAsCopy>(saver.save(note.file, note.edits, note.files))
        assertTrue(result.name, result.name.matches(Regex("""Lecture \(conflict \d{4}-\d\d-\d\d \d\d-\d\d\)\.xopp""")))
        assertEquals(File(testData, "physics.xopp").length(), elsewhere.length())
        assertEquals(
            stroke,
            read(result.name)
                .document.pages[0]
                .layers
                .last()
                .elements
                .last(),
        )
        // Later saves go to the copy.
        assertEquals(result.name, note.file.name)
        val another = stroke.copy(color = Color.of(0, 0, 0))
        note.edits.pageEdited(0, note.document.pages[0].withElementAdded(another), version = 2)
        assertEquals(SaveResult.Saved, saver.save(note.file, note.edits, note.files))
        assertEquals(another, lastElement(result.name))
    }

    @Test
    fun zipPackagesAreReadOnly() {
        val note = open("xournalpp/packaged_xopp/imgBackground/new.xopp", "Package.xopp")
        assertFalse(note.file.writable)
        note.write()
        val bytes = File(directory, "Package.xopp").readBytes()
        assertEquals(SaveResult.ReadOnly, saver.save(note.file, note.edits, note.files))
        assertTrue(bytes.contentEquals(File(directory, "Package.xopp").readBytes()))
    }
}
