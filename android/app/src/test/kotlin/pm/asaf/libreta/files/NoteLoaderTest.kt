// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.library.FakeDocumentsProvider
import pm.asaf.libreta.store.SafDocumentStore
import java.io.File
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class NoteLoaderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = SafDocumentStore(context.contentResolver)
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
    }

    private fun load(xml: String): NoteState {
        File(directory, "Note.xopp").writeText(xml)
        return NoteLoader.load(context, store, FakeDocumentsProvider.fileId("Note.xopp"), "Note.xopp", null)
    }

    @Test
    fun pagesOfNoSizeAreADamagedNote() {
        // Nothing of them could be drawn, nor saved: the note isn't opened (and stays as it is).
        for (size in listOf(
            "width=\"0\" height=\"842\"",
            "width=\"595\" height=\"-1\"",
            "width=\"nan\" height=\"842\"",
        )) {
            val state = load("<xournal><page $size><layer/></page></xournal>")
            assertIs<NoteState.Failed>(state, size)
        }
        assertIs<NoteState.Loaded>(load("<xournal><page width=\"595\" height=\"842\"><layer/></page></xournal>"))
    }
}
