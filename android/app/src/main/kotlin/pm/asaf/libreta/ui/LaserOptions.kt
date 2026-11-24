// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.LaserLook

/**
 * Whether the laser pointer draws like the pen or the highlighter, then its colours and widths;
 * it has no drawing types, as it only draws freehand.
 */
@Composable
internal fun RowScope.LaserOptions(
    controls: ToolControls,
    compact: Boolean,
    onAddColor: () -> Unit,
) {
    val laser = controls.tools.laser
    for ((look, label) in LASER_LOOKS) {
        FilterChip(
            selected = laser.look == look,
            onClick = { controls.onChange { it.copy(laser = it.laser.copy(look = look)) } },
            label = { Text(stringResource(label)) },
        )
    }
    Separator()
    InkOptions(laser.color, laser.size, controls, compact, shapes = false, onAddColor = onAddColor)
}

private val LASER_LOOKS =
    listOf(
        LaserLook.PEN to R.string.tool_pen,
        LaserLook.HIGHLIGHTER to R.string.tool_highlighter,
    )
