// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

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
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.EraserType
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.SelectMode
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.Color as InkColor

/**
 * The tools: pen, highlighter and eraser, then the chosen tool's colour (the fixed colours
 * from Settings, recent custom colours, and a picker) and width (and the pen's pressure), or the
 * eraser's type and size; and finger drawing.
 */
@Composable
fun ToolBar(
    controls: ToolControls,
    modifier: Modifier = Modifier,
    /** Restyles the selection (with the selection tool): a colour or a width, null for neither. */
    onRestyle: (InkColor?, PenSize?) -> Unit = { _, _ -> },
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
            ToolButton(Tool.SELECT, SelectIcon, R.string.tool_select, controls)
            ToolButton(Tool.TEXT, TextIcon, R.string.tool_text, controls)
            Separator()
            when (tools.tool) {
                Tool.PEN -> {
                    InkOptions(tools.pen.color, tools.pen.size, controls) { picking = true }
                    Separator()
                    Toggle(PressureIcon, R.string.pen_pressure, tools.pen.pressure) { pressure ->
                        controls.onChange { it.copy(pen = it.pen.copy(pressure = pressure)) }
                    }
                }

                Tool.HIGHLIGHTER -> {
                    InkOptions(tools.highlighter.color, tools.highlighter.size, controls) { picking = true }
                }

                Tool.ERASER -> {
                    EraserOptions(controls)
                }

                Tool.SELECT -> {
                    SelectOptions(controls, onRestyle) { picking = true }
                }

                Tool.TEXT -> {
                    TextOptions(controls) { picking = true }
                }
            }
            if (controls.fingerButton) {
                Separator()
                Toggle(FingerIcon, R.string.draw_with_finger, controls.fingerDraws, controls.onFingerDraws)
            }
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
                if (tools.tool == Tool.SELECT) onRestyle(it, null)
            },
            onDismiss = { picking = false },
        )
    }
}

/** A setting turned on and off, shown by its [icon]: filled while on. */
@Composable
private fun Toggle(
    icon: ImageVector,
    label: Int,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val name = stringResource(label)
    WithTooltip(name) { FilledIconToggleButton(checked = on, onCheckedChange = onChange) { Icon(icon, name) } }
}

@Composable
private fun ToolButton(
    tool: Tool,
    icon: ImageVector,
    label: Int,
    controls: ToolControls,
) {
    val name = stringResource(label)
    WithTooltip(name) {
        FilledIconToggleButton(checked = controls.tools.tool == tool, onCheckedChange = { controls.onTool(tool) }) {
            Icon(icon, name)
        }
    }
}

/**
 * The fixed colours, recent ones and the current one if it is neither, then the widths
 * offered (and the current one), for the pen or highlighter.
 */
@Composable
private fun RowScope.InkOptions(
    color: InkColor,
    size: PenSize,
    controls: ToolControls,
    onAddColor: () -> Unit,
) {
    ShapeMenu(controls)
    Separator()
    val swatches = (controls.fixedColors + controls.recentColors + color).distinct()
    for (swatch in swatches) {
        Swatch(swatch, colorName(swatch), selected = swatch == color) { controls.onColor(swatch) }
    }
    AddColorButton(onAddColor)
    Separator()
    for (option in PenSize.entries.filter { it in controls.visibleSizes || it == size }) {
        WidthOption(option, color.toCompose(), CircleShape, selected = option == size) { controls.onSize(option) }
    }
}

/** How the selection tool encloses what it selects. */
@Composable
private fun RowScope.SelectOptions(
    controls: ToolControls,
    onRestyle: (InkColor?, PenSize?) -> Unit,
    onAddColor: () -> Unit,
) {
    for ((mode, label) in SELECT_MODES) {
        FilterChip(
            selected = controls.tools.selectMode == mode,
            onClick = { controls.onSelectMode(mode) },
            label = { Text(stringResource(label)) },
        )
    }
    Separator()
    // Colours and widths restyle the selection, as in Xournal++.
    for (swatch in (controls.fixedColors + controls.recentColors).distinct()) {
        Swatch(swatch, colorName(swatch), selected = false) { onRestyle(swatch, null) }
    }
    AddColorButton(onAddColor)
    Separator()
    for (option in PenSize.entries.filter { it in controls.visibleSizes }) {
        WidthOption(option, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape, selected = false) {
            onRestyle(null, option)
        }
    }
}

/** The eraser's type and the sizes offered. */
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
    for (option in PenSize.entries.filter { it in controls.visibleSizes || it == eraser.size }) {
        val shape = RoundedCornerShape(2.dp)
        WidthOption(option, MaterialTheme.colorScheme.onSurfaceVariant, shape, selected = option == eraser.size) {
            controls.onSize(option)
        }
    }
}

/** Picks a colour to add. */
@Composable
internal fun AddColorButton(onClick: () -> Unit) {
    TooltipIconButton(AddIcon, stringResource(R.string.add_color), onClick = onClick)
}

@Composable
internal fun Separator() = VerticalDivider(Modifier.padding(horizontal = 6.dp).size(width = 1.dp, height = 28.dp))

@Composable
internal fun Swatch(
    color: InkColor,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.tool_color, name)
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    WithTooltip(description) {
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
    WithTooltip(description) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(32.dp)
                    .border(2.dp, ring, CircleShape)
                    .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                    .semantics { contentDescription = description },
        ) {
            WidthDot(size, color, shape)
        }
    }
}

/** A width as the toolbar shows it: a dot (or square) growing with the size. */
@Composable
internal fun WidthDot(
    size: PenSize,
    color: Color,
    shape: Shape = CircleShape,
) {
    Box(Modifier.size(DOT_SIZES[size.ordinal].dp).background(color, shape))
}

/** The name of a width. */
internal val PenSize.label: Int
    get() =
        when (this) {
            PenSize.VERY_FINE -> R.string.width_very_fine
            PenSize.FINE -> R.string.width_fine
            PenSize.MEDIUM -> R.string.width_medium
            PenSize.THICK -> R.string.width_thick
            PenSize.VERY_THICK -> R.string.width_very_thick
        }

private val SELECT_MODES =
    listOf(
        SelectMode.RECTANGLE to R.string.select_rectangle,
        SelectMode.LASSO to R.string.select_lasso,
    )

private val ERASER_TYPES =
    listOf(
        EraserType.STANDARD to R.string.eraser_standard,
        EraserType.WHITEOUT to R.string.eraser_whiteout,
        EraserType.DELETE_STROKE to R.string.eraser_delete_stroke,
    )

/** Dot sizes in dp, from very fine to very thick. */
private val DOT_SIZES = listOf(4, 7, 10, 14, 20)
