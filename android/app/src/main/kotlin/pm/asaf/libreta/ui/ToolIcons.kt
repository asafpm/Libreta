// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

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

/** Drawing with a finger: a hand pointing up with its index finger. */
@Suppress("MagicNumber") // path coordinates
val FingerIcon: ImageVector =
    lineIcon("Finger") {
        // The index finger, then the knuckles of the curled ones, the palm and the thumb.
        moveTo(9f, 13.5f)
        lineTo(9f, 4.5f)
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 4.5f)
        lineTo(12f, 11f)
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15f, 11f)
        lineTo(15f, 11.5f)
        arcTo(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17.5f, 11.5f)
        lineTo(17.5f, 12.5f)
        arcTo(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 12.5f)
        lineTo(20f, 16f)
        quadTo(20f, 21f, 15.5f, 21f)
        lineTo(12.5f, 21f)
        quadTo(10.5f, 21f, 9f, 19.5f)
        lineTo(5.5f, 16f)
        arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.5f, 14f)
        lineTo(9f, 15.5f)
    }

/** Pen pressure: a stroke swelling and thinning as a pen pressed harder and softer draws it. */
@Suppress("MagicNumber") // path coordinates
val PressureIcon: ImageVector =
    ImageVector
        .Builder(
            name = "Pressure",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(2.5f, 17.5f)
            curveTo(6f, 17.5f, 7f, 5.5f, 11f, 5.5f)
            curveTo(15f, 5.5f, 14.5f, 16f, 17.5f, 16f)
            curveTo(19f, 16f, 20.3f, 14f, 21.5f, 11f)
            curveTo(21.2f, 15f, 19.8f, 19f, 17.5f, 19f)
            curveTo(12.8f, 19f, 13.5f, 9f, 11f, 9f)
            curveTo(8.5f, 9f, 7.5f, 17.5f, 2.5f, 17.5f)
            close()
        }.build()

/** Help: a question mark in a circle. */
@Suppress("MagicNumber") // path coordinates
val HelpIcon: ImageVector =
    lineIcon("Help") {
        moveTo(3f, 12f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 12f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 12f)
        moveTo(9.5f, 9.5f)
        arcTo(2.6f, 2.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 13.4f, 11.7f)
        quadTo(12f, 12.5f, 12f, 14f)
        moveTo(12f, 17.2f)
        lineTo(12f, 17.25f)
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

/** Pages: two sheets, one behind the other. */
@Suppress("MagicNumber") // path coordinates
val PagesIcon: ImageVector =
    lineIcon("Pages") {
        moveTo(8f, 3f)
        lineTo(19f, 3f)
        lineTo(19f, 17f)
        moveTo(5f, 6f)
        lineTo(15f, 6f)
        lineTo(15f, 21f)
        lineTo(5f, 21f)
        close()
    }

/** Insert page: a sheet with a plus. */
@Suppress("MagicNumber") // path coordinates
val AddPageIcon: ImageVector =
    lineIcon("Add page") {
        moveTo(6f, 3f)
        lineTo(18f, 3f)
        lineTo(18f, 21f)
        lineTo(6f, 21f)
        close()
        moveTo(12f, 8f)
        lineTo(12f, 16f)
        moveTo(8f, 12f)
        lineTo(16f, 12f)
    }

/** Export: an arrow up out of a tray. */
@Suppress("MagicNumber") // path coordinates
val ExportIcon: ImageVector =
    lineIcon("Export") {
        moveTo(4f, 14f)
        lineTo(4f, 20f)
        lineTo(20f, 20f)
        lineTo(20f, 14f)
        moveTo(12f, 15f)
        lineTo(12f, 4f)
        moveTo(8f, 8f)
        lineTo(12f, 4f)
        lineTo(16f, 8f)
    }

/** A picture: a frame with a mountain and the sun. */
@Suppress("MagicNumber") // path coordinates
val ImageIcon: ImageVector =
    lineIcon("Image") {
        moveTo(3f, 5f)
        lineTo(21f, 5f)
        lineTo(21f, 19f)
        lineTo(3f, 19f)
        close()
        moveTo(3f, 16f)
        lineTo(9f, 11f)
        lineTo(14f, 16f)
        moveTo(13f, 14f)
        lineTo(16f, 12f)
        lineTo(21f, 16f)
        moveTo(16f, 9f)
        lineTo(16.5f, 9f)
    }

/** A camera. */
@Suppress("MagicNumber") // path coordinates
val CameraIcon: ImageVector =
    lineIcon("Camera") {
        moveTo(3f, 8f)
        lineTo(7f, 8f)
        lineTo(9f, 5f)
        lineTo(15f, 5f)
        lineTo(17f, 8f)
        lineTo(21f, 8f)
        lineTo(21f, 19f)
        lineTo(3f, 19f)
        close()
        moveTo(12f, 10f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 16f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 10f)
    }

/** Text: a T. */
@Suppress("MagicNumber") // path coordinates
val TextIcon: ImageVector =
    lineIcon("Text") {
        moveTo(5f, 5f)
        lineTo(19f, 5f)
        moveTo(12f, 5f)
        lineTo(12f, 20f)
        moveTo(9f, 20f)
        lineTo(15f, 20f)
    }

/** Freehand: a squiggle. */
@Suppress("MagicNumber") // path coordinates
val FreehandIcon: ImageVector =
    lineIcon("Freehand") {
        moveTo(3f, 17f)
        curveTo(6f, 7f, 9f, 7f, 11f, 13f)
        curveTo(13f, 19f, 17f, 19f, 21f, 7f)
    }

/** A straight line. */
@Suppress("MagicNumber") // path coordinates
val LineIcon: ImageVector =
    lineIcon("Line") {
        moveTo(4f, 20f)
        lineTo(20f, 4f)
    }

/** A rectangle. */
@Suppress("MagicNumber") // path coordinates
val RectangleIcon: ImageVector =
    lineIcon("Rectangle") {
        moveTo(3f, 6f)
        lineTo(21f, 6f)
        lineTo(21f, 18f)
        lineTo(3f, 18f)
        close()
    }

/** An ellipse. */
@Suppress("MagicNumber") // path coordinates
val EllipseIcon: ImageVector =
    lineIcon("Ellipse") {
        moveTo(3f, 12f)
        arcTo(9f, 6f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 21f, y1 = 12f)
        arcTo(9f, 6f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 3f, y1 = 12f)
    }

/** An arrow. */
@Suppress("MagicNumber") // path coordinates
val ArrowIcon: ImageVector =
    lineIcon("Arrow") {
        moveTo(4f, 20f)
        lineTo(20f, 4f)
        moveTo(11f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 13f)
    }

/** A double arrow. */
@Suppress("MagicNumber") // path coordinates
val DoubleArrowIcon: ImageVector =
    lineIcon("Double arrow") {
        moveTo(4f, 20f)
        lineTo(20f, 4f)
        moveTo(12f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 12f)
        moveTo(4f, 12f)
        lineTo(4f, 20f)
        lineTo(12f, 20f)
    }

/** A coordinate system: two axes. */
@Suppress("MagicNumber") // path coordinates
val AxesIcon: ImageVector =
    lineIcon("Coordinate system") {
        moveTo(5f, 3f)
        lineTo(5f, 19f)
        lineTo(21f, 19f)
    }

/** New folder: a folder with a plus. */
@Suppress("MagicNumber") // path coordinates
val NewFolderIcon: ImageVector =
    lineIcon("New folder") {
        moveTo(3f, 5f)
        lineTo(9f, 5f)
        lineTo(11f, 7f)
        lineTo(21f, 7f)
        lineTo(21f, 19f)
        lineTo(3f, 19f)
        close()
        moveTo(12f, 10f)
        lineTo(12f, 16f)
        moveTo(9f, 13f)
        lineTo(15f, 13f)
    }

/** Duplicate: two sheets, the front one with a plus. */
@Suppress("MagicNumber") // path coordinates
val DuplicateIcon: ImageVector =
    lineIcon("Duplicate") {
        moveTo(4f, 15f)
        lineTo(4f, 4f)
        lineTo(15f, 4f)
        moveTo(8f, 8f)
        lineTo(20f, 8f)
        lineTo(20f, 20f)
        lineTo(8f, 20f)
        close()
        moveTo(14f, 11f)
        lineTo(14f, 17f)
        moveTo(11f, 14f)
        lineTo(17f, 14f)
    }

/** Page background: a ruled sheet. */
@Suppress("MagicNumber") // path coordinates
val BackgroundIcon: ImageVector =
    lineIcon("Background") {
        moveTo(5f, 3f)
        lineTo(19f, 3f)
        lineTo(19f, 21f)
        lineTo(5f, 21f)
        close()
        moveTo(8f, 8f)
        lineTo(16f, 8f)
        moveTo(8f, 12f)
        lineTo(16f, 12f)
        moveTo(8f, 16f)
        lineTo(16f, 16f)
    }

/** Layers: stacked sheets seen at an angle. */
@Suppress("MagicNumber") // path coordinates
val LayersIcon: ImageVector =
    lineIcon("Layers") {
        moveTo(12f, 3f)
        lineTo(21f, 8f)
        lineTo(12f, 13f)
        lineTo(3f, 8f)
        close()
        moveTo(3f, 12f)
        lineTo(12f, 17f)
        lineTo(21f, 12f)
        moveTo(3f, 16f)
        lineTo(12f, 21f)
        lineTo(21f, 16f)
    }

/** Shown: an open eye. */
@Suppress("MagicNumber") // path coordinates
val EyeIcon: ImageVector =
    lineIcon("Shown") {
        moveTo(2f, 12f)
        quadTo(12f, 2f, 22f, 12f)
        quadTo(12f, 22f, 2f, 12f)
        close()
        moveTo(12f, 9f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 15f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 9f)
    }

/** Hidden: a crossed-out eye. */
@Suppress("MagicNumber") // path coordinates
val EyeOffIcon: ImageVector =
    lineIcon("Hidden") {
        moveTo(2f, 12f)
        quadTo(12f, 2f, 22f, 12f)
        quadTo(12f, 22f, 2f, 12f)
        close()
        moveTo(4f, 4f)
        lineTo(20f, 20f)
    }

/** Merge down: an arrow onto a line. */
@Suppress("MagicNumber") // path coordinates
val MergeDownIcon: ImageVector =
    lineIcon("Merge down") {
        moveTo(12f, 3f)
        lineTo(12f, 15f)
        moveTo(7f, 10f)
        lineTo(12f, 15f)
        lineTo(17f, 10f)
        moveTo(4f, 20f)
        lineTo(20f, 20f)
    }

/** Select: a dashed square. */
@Suppress("MagicNumber") // path coordinates
val SelectIcon: ImageVector =
    lineIcon("Select") {
        for (i in 0..2) {
            val a = 4f + i * 6f
            moveTo(a, 4f)
            lineTo(a + 3f, 4f)
            moveTo(a, 20f)
            lineTo(a + 3f, 20f)
            moveTo(4f, a)
            lineTo(4f, a + 3f)
            moveTo(20f, a)
            lineTo(20f, a + 3f)
        }
    }

/** Paste: a clipboard. */
@Suppress("MagicNumber") // path coordinates
val PasteIcon: ImageVector =
    lineIcon("Paste") {
        moveTo(9f, 4f)
        lineTo(5f, 4f)
        lineTo(5f, 21f)
        lineTo(19f, 21f)
        lineTo(19f, 4f)
        lineTo(15f, 4f)
        moveTo(9f, 3f)
        lineTo(15f, 3f)
        lineTo(15f, 6f)
        lineTo(9f, 6f)
        close()
        moveTo(9f, 12f)
        lineTo(15f, 12f)
        moveTo(9f, 16f)
        lineTo(15f, 16f)
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
