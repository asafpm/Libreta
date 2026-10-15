// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import okio.Path
import okio.Path.Companion.toPath

internal actual val testDataDir: Path =
    checkNotNull(System.getProperty("libreta.testdata")) { "system property libreta.testdata is not set" }.toPath()
