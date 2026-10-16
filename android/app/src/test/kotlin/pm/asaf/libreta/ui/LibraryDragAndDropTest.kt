// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.asaf.libreta.core.store.FileVersion
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.LibraryEntry

class LibraryDragAndDropTest {
    private val root = FolderRef(StoreId("root"), "Notes")
    private val courses = FolderRef(StoreId("root/Courses"), "Courses")
    private val physics = FolderRef(StoreId("root/Physics"), "Physics")

    @Test
    fun cardsDropIntoOtherFolders() {
        val note = LibraryEntry.Document(StoreId("root/a.xopp"), "a.xopp", FileVersion(0, 0), root)
        val folder = LibraryEntry.Folder(courses, 0, root)
        assertTrue(canDrop(note, courses))
        assertTrue(canDrop(folder, physics))
        // Not where it is, nor into itself.
        assertFalse(canDrop(note, root))
        assertFalse(canDrop(folder, root))
        assertFalse(canDrop(folder, courses))
        // Up, from a folder below (a breadcrumb).
        val below = LibraryEntry.Document(StoreId("root/Courses/b.xopp"), "b.xopp", FileVersion(0, 0), courses)
        assertTrue(canDrop(below, root))
    }

    @Test
    fun scrollsNearTheGridsEdges() {
        val tracker = DragTracker().apply { area = Rect(0f, 100f, 800f, 1100f) }
        assertEquals(0, tracker.direction(EDGE))
        tracker.y = 120f
        assertEquals(-1, tracker.direction(EDGE))
        tracker.y = 600f
        assertEquals(0, tracker.direction(EDGE))
        tracker.y = 1090f
        assertEquals(1, tracker.direction(EDGE))
    }

    private companion object {
        const val EDGE = 50f
    }
}
