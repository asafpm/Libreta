// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

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
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.PageChange
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.StrokeTool
import pm.asaf.libreta.library.FakeDocumentsProvider
import pm.asaf.libreta.library.FolderListing
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.store.SafDocumentStore
import java.io.File
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteSaverTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testData = File(checkNotNull(System.getProperty("libreta.testdata")))
    private val store = SafDocumentStore(context.contentResolver)
    private lateinit var backups: Backups
    private lateinit var saver: NoteSaver
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
        backups = Backups(temporary.newFolder("backups"))
        saver = NoteSaver(context, store, backups)
        FakeDocumentsProvider.install(directory)
        root = FolderRef(FakeDocumentsProvider.folderId(), "Notes")
    }

    /** Opens testdata [sample] copied into the library as [name]. */
    private fun open(
        sample: String,
        name: String,
    ): NoteState.Loaded {
        File(testData, sample).copyTo(File(directory, name))
        val entry =
            FolderListing.list(store, root).filterIsInstance<LibraryEntry.Document>().single {
                it.name ==
                    name
            }
        return NoteLoader.load(context, store, entry.id, name, DocumentLocation(listOf(root), name)) as NoteState.Loaded
    }

    private fun NoteState.Loaded.write(element: Element = stroke) {
        val page = session.document.pages[0]
        session.edited(Edit(listOf(PageChange(0, page, page.withElementAdded(element)))))
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
        assertEquals(SaveResult.Saved, saver.save(note.file, note.session, note.files))
        assertFalse(note.session.dirty)

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
        assertEquals("Libreta 0.1.0", saved.document.creator)
        // A new Xournal++-sized preview: the longer side 128 px.
        val preview = checkNotNull(saved.document.preview).toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(preview, 0, preview.size)
        assertEquals(128, maxOf(bitmap.width, bitmap.height))
        // Saving again without changes does nothing.
        val modified = File(directory, "Lecture.xopp").lastModified()
        assertEquals(SaveResult.Unchanged, saver.save(note.file, note.session, note.files))
        assertEquals(modified, File(directory, "Lecture.xopp").lastModified())
    }

    @Test
    fun theOriginalIsBackedUpOncePerSession() {
        val original = File(testData, "note-taking.xopp").readBytes()
        val note = open("note-taking.xopp", "Lecture.xopp")
        note.write()
        saver.save(note.file, note.session, note.files)
        note.write(stroke.copy(width = 1.0))
        saver.save(note.file, note.session, note.files)
        val id = note.file.id
        assertTrue(original.contentEquals(backups.of(id).single().readBytes()))

        // Opened again: what the last session saved is backed up before it is overwritten.
        val saved = File(directory, "Lecture.xopp").readBytes()
        val again = NoteLoader.load(context, store, id, "Lecture.xopp", DocumentLocation(listOf(root), "Lecture.xopp"))
        (again as NoteState.Loaded).write()
        saver.save(again.file, again.session, again.files)
        assertEquals(2, backups.of(id).size)
        assertTrue(saved.contentEquals(backups.of(id).last().readBytes()))
    }

    @Test
    fun aFileChangedElsewhereIsNotOverwritten() {
        val note = open("note-taking.xopp", "Lecture.xopp")
        note.write()
        // E.g. a sync app replaced it meanwhile.
        val elsewhere = File(directory, "Lecture.xopp")
        File(testData, "physics.xopp").copyTo(elsewhere, overwrite = true)
        elsewhere.setLastModified(elsewhere.lastModified() + 60_000)

        val result = assertIs<SaveResult.SavedAsCopy>(saver.save(note.file, note.session, note.files))
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
        note.write(another)
        assertEquals(SaveResult.Saved, saver.save(note.file, note.session, note.files))
        assertEquals(another, lastElement(result.name))
    }

    @Test
    fun zipPackagesAreReadOnly() {
        val note = open("xournalpp/packaged_xopp/imgBackground/new.xopp", "Package.xopp")
        assertFalse(note.file.writable)
        note.write()
        val bytes = File(directory, "Package.xopp").readBytes()
        assertEquals(SaveResult.ReadOnly, saver.save(note.file, note.session, note.files))
        assertTrue(bytes.contentEquals(File(directory, "Package.xopp").readBytes()))
    }
}
