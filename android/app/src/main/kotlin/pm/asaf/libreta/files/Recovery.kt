// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.buffer
import okio.sink
import pm.asaf.libreta.core.store.StoreId
import java.io.File
import java.io.IOException

/**
 * Notes as they are being saved, in app-private storage, until their files have them: a save
 * cut off (the app killed while writing) or failing (the provider offline) loses nothing that
 * was edited, and the next start of the app offers what is here. `<dir>/<note>.xopp`, with the
 * note's id and name in `<dir>/<note>.txt`.
 */
class Recovery(
    private val dir: File,
) {
    /** What is kept of the note [id], named [name]. */
    class Copy(
        val id: StoreId,
        val name: String,
        private val file: File,
    ) {
        fun bytes(): ByteString = file.readBytes().toByteString()
    }

    /** Keeps [bytes], the note [id] named [name] about to be saved. Blocking. */
    @Synchronized
    @Throws(IOException::class)
    fun keep(
        id: StoreId,
        name: String,
        bytes: ByteString,
    ) {
        dir.mkdirs()
        val key = keyOf(id)
        val file = File(dir, "$key.xopp")
        val partial = File(dir, "$key.xopp.partial")
        try {
            partial.sink().buffer().use { it.write(bytes) }
            File(dir, "$key.txt").writeText("${id.value}\n$name\n")
            if (!partial.renameTo(file)) throw IOException("Cannot keep $file")
        } finally {
            partial.delete()
        }
    }

    /** Forgets the note [id]: its file has what was kept (or the user chose to discard it). */
    @Synchronized
    fun forget(id: StoreId) {
        val key = keyOf(id)
        File(dir, "$key.xopp").delete()
        File(dir, "$key.txt").delete()
    }

    /** What is kept. */
    @Synchronized
    fun copies(): List<Copy> =
        dir.listFiles { file -> file.name.endsWith(".txt") }.orEmpty().mapNotNull { source ->
            val file = File(dir, source.name.removeSuffix(".txt") + ".xopp")
            val (id, name) = source.readLines().takeIf { it.size >= 2 } ?: return@mapNotNull null
            if (file.isFile) Copy(StoreId(id), name, file) else null
        }

    private fun keyOf(id: StoreId) =
        id.value
            .encodeUtf8()
            .sha256()
            .hex()
}
