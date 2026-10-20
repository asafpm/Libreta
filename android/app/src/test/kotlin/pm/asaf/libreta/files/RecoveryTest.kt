// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pm.asaf.libreta.core.store.StoreId
import java.io.File

class RecoveryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val lecture = StoreId("content://notes/Lecture.xopp")
    private val seminar = StoreId("content://notes/Seminar.xopp")

    @Test
    fun keepsWhatIsBeingSavedUntilItIsSaved() {
        val directory = temporary.newFolder("recovery")
        val recovery = Recovery(directory)
        recovery.keep(lecture, "Lecture.xopp", "first".encodeUtf8())
        recovery.keep(lecture, "Lecture.xopp", "second".encodeUtf8())
        recovery.keep(seminar, "Seminar.xopp", "seminar".encodeUtf8())
        // As another start of the app finds them.
        val found = Recovery(directory).copies().sortedBy { it.name }
        assertEquals(listOf("Lecture.xopp", "Seminar.xopp"), found.map { it.name })
        assertEquals(lecture, found[0].id)
        assertEquals("second", found[0].bytes().utf8())
        recovery.forget(lecture)
        assertEquals(listOf(seminar), Recovery(directory).copies().map { it.id })
    }

    @Test
    fun aCopyCutOffWhileWrittenIsNotOne() {
        val directory = temporary.newFolder("recovery")
        Recovery(directory).keep(lecture, "Lecture.xopp", "whole".encodeUtf8())
        // A copy whose writing was cut off is only a partial file.
        File(directory, "cut.xopp.partial").writeText("half")
        assertEquals(listOf(lecture), Recovery(directory).copies().map { it.id })
    }
}
