// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import pm.asaf.libreta.core.geometry.LineCap
import pm.asaf.libreta.core.geometry.PathCommand
import pm.asaf.libreta.core.geometry.ShapePath
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Color
import kotlin.math.PI

private const val DEGREES_PER_RADIAN = 180.0 / PI
private const val OPAQUE_ALPHA = 0xff shl 24
private const val RGB_MASK = 0xffffff
private const val ALPHA_BITS = 8

/** The colour as an opaque ARGB int; rendering ignores the alpha stored in files (docs/rendering.md §3.1). */
internal fun Color.toOpaqueArgb(): Int = OPAQUE_ALPHA or ((rgba.toInt() ushr ALPHA_BITS) and RGB_MASK)

internal fun LineCap.toPaintCap(): Paint.Cap =
    when (this) {
        LineCap.BUTT -> Paint.Cap.BUTT
        LineCap.ROUND -> Paint.Cap.ROUND
        LineCap.SQUARE -> Paint.Cap.SQUARE
    }

internal fun Bounds.toRectF(): RectF = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

/** Converts a geometry path; arcs follow cairo's semantics (see [PathCommand.Arc]). */
internal fun ShapePath.toAndroidPath(): Path {
    val path = Path()
    val oval = RectF()
    for (command in commands) {
        when (command) {
            is PathCommand.MoveTo -> {
                path.moveTo(command.x.toFloat(), command.y.toFloat())
            }

            is PathCommand.LineTo -> {
                path.lineTo(command.x.toFloat(), command.y.toFloat())
            }

            is PathCommand.Arc -> {
                oval.set(
                    (command.centerX - command.radius).toFloat(),
                    (command.centerY - command.radius).toFloat(),
                    (command.centerX + command.radius).toFloat(),
                    (command.centerY + command.radius).toFloat(),
                )
                // Path.arcTo takes the sweep modulo 360°, so arcs are added as two halves.
                val start = command.startAngle * DEGREES_PER_RADIAN
                val half = command.sweep * DEGREES_PER_RADIAN / 2
                path.arcTo(oval, start.toFloat(), half.toFloat(), false)
                path.arcTo(oval, (start + half).toFloat(), half.toFloat(), false)
            }

            PathCommand.Close -> {
                path.close()
            }
        }
    }
    return path
}
