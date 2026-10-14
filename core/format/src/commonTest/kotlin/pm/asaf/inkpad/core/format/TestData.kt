// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

import okio.ByteString
import okio.FileSystem
import okio.Path

/** Root of the repository's `testdata/` directory, provided by the Gradle test task. */
internal expect val testDataDir: Path

internal object TestData {
    fun read(relativePath: String): ByteString = FileSystem.SYSTEM.read(testDataDir / relativePath) { readByteString() }
}
