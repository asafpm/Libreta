// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.ByteString
import okio.Source
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.FileVersion
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreException
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.core.store.WriteResult
import pm.asaf.libreta.library.FolderListing
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.FolderState
import pm.asaf.libreta.library.PreviewCache

@RunWith(AndroidJUnit4::class)
class LibraryGridTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val folder = FolderRef(StoreId("folder"), "Notes")

    /**
     * A store listing [entries] as they are, duplicates included, as some providers do (e.g.
     * Nextcloud's while uploading).
     */
    private class ListingStore(
        private val entries: List<StoreEntry>,
    ) : DocumentStore {
        override fun list(folder: StoreId) = entries

        override fun entry(id: StoreId): StoreEntry? = entries.firstOrNull { it.id == id }

        override fun read(file: StoreId): Source = throw StoreException("not here")

        override fun write(
            file: StoreId,
            bytes: ByteString,
            expected: FileVersion?,
        ): WriteResult = throw StoreException("not here")

        override fun create(
            folder: StoreId,
            name: String,
        ): StoreEntry.File = throw StoreException("not here")

        override fun createFolder(
            folder: StoreId,
            name: String,
        ): StoreEntry.Folder = throw StoreException("not here")

        override fun delete(file: StoreId) = throw StoreException("not here")

        override fun rename(
            id: StoreId,
            name: String,
        ) = throw StoreException("not here")

        override fun move(
            id: StoreId,
            from: StoreId,
            to: StoreId,
        ) = throw StoreException("not here")

        override fun pathTo(
            root: StoreId,
            file: StoreId,
        ): List<StoreEntry.Folder>? = null
    }

    private fun file(
        id: String,
        name: String,
    ) = StoreEntry.File(StoreId(id), name, FileVersion(1_000, 10))

    @Test
    fun aFileListedTwiceIsShownOnce() {
        val twice = file("a", "slides_annotated.pdf")
        val listed = FolderListing.list(ListingStore(listOf(twice, twice, file("b", "note.xopp"))), folder)
        assertEquals(listOf("slides_annotated.pdf", "note.xopp").sorted(), listed.map { it.name }.sorted())
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun filesOfTheSameNameAreBothShown() {
        // Two documents of one name (different ids): the grid shows both rather than failing.
        val store = ListingStore(listOf(file("a", "slides.xopp"), file("b", "slides.xopp")))
        val entries = FolderListing.list(store, folder)
        val actions = LibraryActions({}, {}, {})
        compose.setContent {
            MaterialTheme {
                LibraryGrid { folderItems("root", FolderState.Loaded(entries), PreviewCache(context, store), actions) }
            }
        }
        compose.waitForIdle()
        assertEquals(2, compose.onAllNodesWithText("slides.xopp").fetchSemanticsNodes().size)
    }
}
