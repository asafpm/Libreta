// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

import okio.Path
import okio.Path.Companion.toPath

internal actual val testDataDir: Path =
    checkNotNull(System.getProperty("inkpad.testdata")) { "system property inkpad.testdata is not set" }.toPath()
