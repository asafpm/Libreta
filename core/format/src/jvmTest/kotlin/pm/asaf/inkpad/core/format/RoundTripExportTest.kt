// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test

/**
 * Writes every sample document as read and re-written by Inkpad, for
 * scripts/xournalpp-crosscheck.sh to compare in Xournal++. Does nothing unless the
 * `inkpad.roundtripDir` system property is set.
 *
 * Layout: `<dir>/original/` is a copy of testdata/, `<dir>/rewritten/` the same tree with
 * each document replaced by Inkpad's output, `<dir>/documents.txt` lists the documents.
 */
class RoundTripExportTest {
    private val fileSystem = FileSystem.SYSTEM

    @Test
    fun exportRewrittenSamples() {
        val outDir = System.getProperty("inkpad.roundtripDir")?.toPath() ?: return
        fileSystem.deleteRecursively(outDir)
        for (side in listOf("original", "rewritten")) copyTree(testDataDir, outDir / side)

        val documents = SampleFiles.own + SampleFiles.xournalppGzipOrPlain + SampleFiles.xournalppZip
        for (document in documents) {
            val result = XoppReader.read(TestData.read(document))
            val target = outDir / "rewritten" / document
            fileSystem.write(target) { write(XoppWriter.toByteString(result.document)) }
            // A gzip document refers to attached files as "<document>.<name>" next to it.
            for ((name, data) in result.attachments.filterKeys { it.startsWith("attachments/") }) {
                val sidecar = target.parentDir() / "${target.name}.$name"
                fileSystem.createDirectories(sidecar.parentDir())
                fileSystem.write(sidecar) { write(data) }
            }
        }
        fileSystem.write(outDir / "documents.txt") { writeUtf8(documents.joinToString("\n", postfix = "\n")) }
    }

    private fun copyTree(
        from: Path,
        to: Path,
    ) {
        for (source in fileSystem.listRecursively(from)) {
            val target = to / source.relativeTo(from)
            if (fileSystem.metadata(source).isDirectory) {
                fileSystem.createDirectories(target)
            } else {
                fileSystem.createDirectories(target.parentDir())
                fileSystem.copy(source, target)
            }
        }
    }

    private fun Path.parentDir(): Path = checkNotNull(parent) { "$this has no parent" }
}
