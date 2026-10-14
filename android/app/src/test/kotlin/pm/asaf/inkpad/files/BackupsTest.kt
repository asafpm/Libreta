// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.files

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pm.asaf.inkpad.core.store.StoreId

class BackupsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private var now = 1_700_000_000_000L
    private val a = StoreId("content://notes/a.xopp")
    private val b = StoreId("content://notes/b.xopp")

    private fun Backups.keep(
        id: StoreId,
        text: String,
    ) {
        keep(id, id.value.substringAfterLast('/')) { Buffer().writeUtf8(text) }
        now += 1000
    }

    @Test
    fun keepsTheNewestCopiesOfEachNote() {
        val backups = Backups(temporary.newFolder(), perNote = 3, clock = { now })
        for (i in 1..5) backups.keep(a, "a$i")
        backups.keep(b, "b1")
        assertEquals(listOf("a3", "a4", "a5"), backups.of(a).map { it.readText() })
        assertEquals(listOf("b1"), backups.of(b).map { it.readText() })
    }

    @Test
    fun keepsWithinTheTotalSize() {
        val backups = Backups(temporary.newFolder(), maxBytes = 10, clock = { now })
        backups.keep(a, "aaaa")
        backups.keep(b, "bbbb")
        // The oldest goes, wherever it is.
        backups.keep(b, "cccc")
        assertEquals(emptyList<String>(), backups.of(a).map { it.readText() })
        assertEquals(listOf("bbbb", "cccc"), backups.of(b).map { it.readText() })
        // The newest stays even when too large alone.
        backups.keep(a, "a".repeat(20))
        assertEquals(listOf("a".repeat(20)), backups.of(a).map { it.readText() })
        assertEquals(emptyList<String>(), backups.of(b))
    }

    @Test
    fun copiesInTheSameMillisecondAreAllKept() {
        val backups = Backups(temporary.newFolder(), clock = { now })
        repeat(2) { backups.keep(a, "same $it") }
        now -= 2000
        backups.keep(a, "again")
        assertEquals(3, backups.of(a).size)
    }
}
