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

/** Pen: a slanted pen with a nib. */
@Suppress("MagicNumber") // path coordinates
val PenIcon: ImageVector =
    lineIcon("Pen") {
        moveTo(4f, 20f)
        lineTo(5.5f, 14.5f)
        lineTo(16f, 4f)
        lineTo(20f, 8f)
        lineTo(9.5f, 18.5f)
        close()
        moveTo(14f, 6f)
        lineTo(18f, 10f)
    }

/** Highlighter: a chisel-tipped marker over a highlighted line. */
@Suppress("MagicNumber") // path coordinates
val HighlighterIcon: ImageVector =
    lineIcon("Highlighter") {
        moveTo(9f, 14f)
        lineTo(16f, 4f)
        lineTo(20f, 7.5f)
        lineTo(12.5f, 17f)
        close()
        moveTo(9f, 14f)
        lineTo(7.5f, 17.5f)
        lineTo(10.5f, 18.5f)
        lineTo(12.5f, 17f)
        moveTo(3f, 21f)
        lineTo(21f, 21f)
    }

/** Eraser: a tilted block with a band, resting on a line. */
@Suppress("MagicNumber") // path coordinates
val EraserIcon: ImageVector =
    lineIcon("Eraser") {
        moveTo(4f, 15f)
        lineTo(13f, 6f)
        lineTo(19f, 12f)
        lineTo(11f, 20f)
        lineTo(9f, 20f)
        close()
        moveTo(8f, 11f)
        lineTo(14f, 17f)
        moveTo(11f, 20f)
        lineTo(20f, 20f)
    }

/** Add: a plus. */
@Suppress("MagicNumber") // path coordinates
val AddIcon: ImageVector =
    lineIcon("Add") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }

/** Remove: a minus. */
@Suppress("MagicNumber") // path coordinates
val RemoveIcon: ImageVector =
    lineIcon("Remove") {
        moveTo(5f, 12f)
        lineTo(19f, 12f)
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
