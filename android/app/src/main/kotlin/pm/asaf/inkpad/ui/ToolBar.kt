// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pm.asaf.inkpad.R
import pm.asaf.inkpad.core.model.EraserType
import pm.asaf.inkpad.core.model.Palette
import pm.asaf.inkpad.core.model.PenSize
import pm.asaf.inkpad.core.model.Tool
import pm.asaf.inkpad.core.model.Color as InkColor

/**
 * The tools: pen, highlighter and eraser, then the chosen tool's colour (Xournal++'s palette,
 * recent custom colours, and a picker) and width, or the eraser's type and size; and finger
 * drawing.
 */
@Composable
fun ToolBar(
    controls: ToolControls,
    modifier: Modifier = Modifier,
) {
    val tools = controls.tools
    var picking by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            ToolButton(Tool.PEN, PenIcon, R.string.tool_pen, controls)
            ToolButton(Tool.HIGHLIGHTER, HighlighterIcon, R.string.tool_highlighter, controls)
            ToolButton(Tool.ERASER, EraserIcon, R.string.tool_eraser, controls)
            Separator()
            when (tools.tool) {
                Tool.PEN -> {
                    InkOptions(
                        tools.pen.color,
                        tools.pen.size,
                        Palette.DEFAULT_PEN to R.string.color_royal_blue,
                        controls,
                    ) {
                        picking =
                            true
                    }
                }

                Tool.HIGHLIGHTER -> {
                    InkOptions(
                        tools.highlighter.color,
                        tools.highlighter.size,
                        Palette.HIGHLIGHTER to R.string.color_bright_yellow,
                        controls,
                    ) { picking = true }
                }

                Tool.ERASER -> {
                    EraserOptions(controls)
                }
            }
            Separator()
            FilterChip(
                selected = controls.fingerDraws,
                onClick = { controls.onFingerDraws(!controls.fingerDraws) },
                label = { Text(stringResource(R.string.draw_with_finger)) },
            )
            Spacer(Modifier.width(6.dp))
        }
    }
    if (picking) {
        val current = if (tools.tool == Tool.HIGHLIGHTER) tools.highlighter.color else tools.pen.color
        ColorPickerDialog(
            initial = current,
            onPick = {
                picking = false
                controls.onCustomColor(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun ToolButton(
    tool: Tool,
    icon: ImageVector,
    label: Int,
    controls: ToolControls,
) {
    FilledIconToggleButton(checked = controls.tools.tool == tool, onCheckedChange = { controls.onTool(tool) }) {
        Icon(icon, stringResource(label))
    }
}

/** Colours, with the tool's own default first, then widths, for the pen or highlighter. */
@Composable
private fun RowScope.InkOptions(
    color: InkColor,
    size: PenSize,
    default: Pair<InkColor, Int>,
    controls: ToolControls,
    onAddColor: () -> Unit,
) {
    val named = listOf(default) + PALETTE
    for ((swatch, name) in named) {
        Swatch(swatch, stringResource(name), selected = swatch == color) { controls.onColor(swatch) }
    }
    // Recent custom colours, and the current one if it is neither.
    val customs =
        (listOf(color) + controls.recentColors).distinct().filter { custom ->
            named.none { it.first == custom }
        }
    for (custom in customs) {
        Swatch(custom, custom.hex(), selected = custom == color) { controls.onColor(custom) }
    }
    IconButton(onClick = onAddColor) { Icon(AddIcon, stringResource(R.string.add_color)) }
    Separator()
    for (option in PenSize.entries) {
        WidthOption(option, color.toCompose(), CircleShape, selected = option == size) { controls.onSize(option) }
    }
}

/** The eraser's type and size. */
@Composable
private fun RowScope.EraserOptions(controls: ToolControls) {
    val eraser = controls.tools.eraser
    for ((type, label) in ERASER_TYPES) {
        FilterChip(
            selected = eraser.type == type,
            onClick = { controls.onEraserType(type) },
            label = { Text(stringResource(label)) },
        )
    }
    Separator()
    for (option in PenSize.entries) {
        val shape = RoundedCornerShape(2.dp)
        WidthOption(option, MaterialTheme.colorScheme.onSurfaceVariant, shape, selected = option == eraser.size) {
            controls.onSize(option)
        }
    }
}

@Composable
private fun Separator() = VerticalDivider(Modifier.padding(horizontal = 6.dp).size(width = 1.dp, height = 28.dp))

@Composable
private fun Swatch(
    color: InkColor,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.tool_color, name)
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
                .padding(4.dp)
                .background(color.toCompose(), CircleShape)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .semantics { contentDescription = description },
    )
}

/** A width, shown as a dot (or square) growing with the size. */
@Composable
private fun WidthOption(
    size: PenSize,
    color: Color,
    shape: Shape,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.tool_width, stringResource(size.label))
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(32.dp)
                .border(2.dp, ring, CircleShape)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .semantics { contentDescription = description },
    ) {
        Box(Modifier.size(DOT_SIZES[size.ordinal].dp).background(color, shape))
    }
}

private val PenSize.label: Int
    get() =
        when (this) {
            PenSize.VERY_FINE -> R.string.width_very_fine
            PenSize.FINE -> R.string.width_fine
            PenSize.MEDIUM -> R.string.width_medium
            PenSize.THICK -> R.string.width_thick
            PenSize.VERY_THICK -> R.string.width_very_thick
        }

/** Xournal++'s palette, with names. */
private val PALETTE: List<Pair<InkColor, Int>> =
    Palette.XOURNALPP.zip(
        listOf(
            R.string.color_yellow,
            R.string.color_orange,
            R.string.color_purple,
            R.string.color_light_green,
            R.string.color_light_blue,
            R.string.color_gray,
            R.string.color_dark_green,
            R.string.color_red,
            R.string.color_dark_blue,
            R.string.color_black,
            R.string.color_white,
        ),
    )

private val ERASER_TYPES =
    listOf(
        EraserType.STANDARD to R.string.eraser_standard,
        EraserType.WHITEOUT to R.string.eraser_whiteout,
        EraserType.DELETE_STROKE to R.string.eraser_delete_stroke,
    )

/** Dot sizes in dp, from very fine to very thick. */
private val DOT_SIZES = listOf(4, 7, 10, 14, 20)
