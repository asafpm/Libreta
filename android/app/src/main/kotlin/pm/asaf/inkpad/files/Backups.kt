// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.files

import okio.ByteString.Companion.encodeUtf8
import okio.Source
import okio.buffer
import okio.sink
import pm.asaf.inkpad.core.store.StoreId
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Copies of notes as they were before Inkpad first overwrote them in a session, in
 * app-private storage: `<dir>/<note>/<time>.xopp`, with the note's id and name in
 * `<dir>/<note>/source.txt`. The newest [perNote] copies of each note are kept, and at most
 * [maxBytes] in all. Restoring them comes later.
 */
class Backups(
    private val dir: File,
    private val perNote: Int = DEFAULT_PER_NOTE,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Keeps a copy of the note [id], named [name], read from [read]. Blocking. */
    @Synchronized
    fun keep(
        id: StoreId,
        name: String,
        read: () -> Source,
    ) {
        val folder = folderOf(id).apply { mkdirs() }
        File(folder, SOURCE_FILE).writeText("${id.value}\n$name\n")
        val stamp = STAMP.format(Instant.ofEpochMilli(clock()))
        // Two in the same millisecond: the later one gets a suffix.
        val file =
            generateSequence(0) { it + 1 }
                .map { File(folder, if (it == 0) "$stamp.xopp" else "$stamp-$it.xopp") }
                .first { !it.exists() }
        val partial = File(folder, "${file.name}.partial")
        try {
            read().use { source -> partial.sink().buffer().use { it.writeAll(source) } }
            check(partial.renameTo(file)) { "cannot keep $file" }
        } finally {
            partial.delete()
        }
        prune(folder, keep = file)
    }

    /** The copies kept of [id], oldest first. */
    fun of(id: StoreId): List<File> = copiesIn(folderOf(id))

    private fun folderOf(id: StoreId) =
        File(
            dir,
            id.value
                .encodeUtf8()
                .sha256()
                .hex()
                .take(KEY_LENGTH),
        )

    private fun copiesIn(folder: File): List<File> =
        folder.listFiles { file -> file.name.endsWith(".xopp") }.orEmpty().sortedBy { it.name }

    /** Drops the oldest copies in [folder] beyond [perNote], then the oldest beyond [maxBytes]; never [keep]. */
    private fun prune(
        folder: File,
        keep: File,
    ) {
        copiesIn(folder).dropLast(perNote).forEach { it.delete() }
        val all =
            dir
                .listFiles()
                .orEmpty()
                .flatMap(::copiesIn)
                .sortedBy { it.name }
        var total = all.sumOf { it.length() }
        for (copy in all.filter { it != keep }) {
            if (total <= maxBytes) break
            total -= copy.length()
            copy.delete()
        }
    }

    private companion object {
        const val DEFAULT_PER_NOTE = 20
        const val DEFAULT_MAX_BYTES = 500L * 1024 * 1024
        const val SOURCE_FILE = "source.txt"
        const val KEY_LENGTH = 32

        /** Sorts by time; UTC, so that clock changes keep the order. */
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
    }
}
