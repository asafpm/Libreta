// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A folder: a tab and a body, on a 24 x 24 grid. */
@Suppress("MagicNumber") // path coordinates
val FolderIcon: ImageVector =
    ImageVector
        .Builder(
            name = "Folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 5f)
            lineTo(9f, 5f)
            lineTo(11f, 7f)
            lineTo(21f, 7f)
            lineTo(21f, 19f)
            lineTo(3f, 19f)
            close()
        }.build()
