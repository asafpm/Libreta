// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.DrawingType

/** What the pen and highlighter draw, as a button showing it and a menu of the others. */
@Composable
fun ShapeMenu(controls: ToolControls) {
    val current = controls.tools.drawingType
    var open by remember { mutableStateOf(false) }
    Box {
        val (icon, label) = SHAPES.getValue(current)
        IconButton(onClick = { open = true }) {
            Icon(icon, stringResource(R.string.shape, stringResource(label)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((type, look) in SHAPES) {
                DropdownMenuItem(
                    text = { Text(stringResource(look.second)) },
                    leadingIcon = { Icon(look.first, contentDescription = null) },
                    onClick = {
                        open = false
                        controls.onChange { it.copy(drawingType = type) }
                    },
                )
            }
        }
    }
}

/** Each drawing type's icon and name. */
private val SHAPES: Map<DrawingType, Pair<ImageVector, Int>> =
    linkedMapOf(
        DrawingType.FREEHAND to (FreehandIcon to R.string.shape_freehand),
        DrawingType.LINE to (LineIcon to R.string.shape_line),
        DrawingType.RECTANGLE to (RectangleIcon to R.string.shape_rectangle),
        DrawingType.ELLIPSE to (EllipseIcon to R.string.shape_ellipse),
        DrawingType.ARROW to (ArrowIcon to R.string.shape_arrow),
        DrawingType.DOUBLE_ARROW to (DoubleArrowIcon to R.string.shape_double_arrow),
        DrawingType.COORDINATE_SYSTEM to (AxesIcon to R.string.shape_axes),
    )
