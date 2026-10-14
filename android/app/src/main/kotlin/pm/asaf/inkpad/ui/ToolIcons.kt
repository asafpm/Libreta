// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Undo: an arrow going back left, then curving down. */
@Suppress("MagicNumber") // path coordinates
val UndoIcon: ImageVector =
    lineIcon("Undo") {
        moveTo(9f, 5f)
        lineTo(4f, 10f)
        lineTo(9f, 15f)
        moveTo(4f, 10f)
        lineTo(14.5f, 10f)
        arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.5f, 19f)
        lineTo(8f, 19f)
    }

/** Redo: [UndoIcon] mirrored. */
@Suppress("MagicNumber") // path coordinates
val RedoIcon: ImageVector =
    lineIcon("Redo") {
        moveTo(15f, 5f)
        lineTo(20f, 10f)
        lineTo(15f, 15f)
        moveTo(20f, 10f)
        lineTo(9.5f, 10f)
        arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9.5f, 19f)
        lineTo(16f, 19f)
    }

/** An icon drawn with 2-unit lines on a 24 x 24 grid. */
@Suppress("MagicNumber") // grid sizes
private fun lineIcon(
    name: String,
    lines: PathBuilder.() -> Unit,
): ImageVector =
    ImageVector
        .Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = lines,
        ).build()
