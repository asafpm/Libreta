// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.Color as InkColor

/** The colour chosen, as a button opening the others ([swatches] and a picker): for narrow toolbars. */
@Composable
internal fun ColourChoice(
    color: InkColor,
    swatches: List<InkColor>,
    onColor: (InkColor) -> Unit,
    onAddColor: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.tool_color, colorName(color))
    Box {
        WithTooltip(description) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.tapTarget(selected = false, onClick = { open = true }).semantics {
                        contentDescription = description
                        role = Role.Button
                    },
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .padding(4.dp)
                        .border(12.dp, color.toCompose(), CircleShape),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.widthIn(max = 240.dp).padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                for (swatch in swatches) {
                    Swatch(swatch, colorName(swatch), selected = swatch == color) {
                        open = false
                        onColor(swatch)
                    }
                }
                AddColorButton {
                    open = false
                    onAddColor()
                }
            }
        }
    }
}

/** The width chosen, as a button opening the [options] (dots growing with the width): for narrow toolbars. */
@Composable
internal fun WidthChoice(
    size: PenSize,
    options: List<PenSize>,
    color: Color,
    onSize: (PenSize) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.tool_width, stringResource(size.label))
    Box {
        WithTooltip(description) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.tapTarget(selected = false, onClick = { open = true }).semantics {
                        contentDescription = description
                        role = Role.Button
                    },
            ) { WidthDot(size, color) }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
                for (option in options) {
                    WidthOption(option, color, CircleShape, selected = option == size) {
                        open = false
                        onSize(option)
                    }
                }
            }
        }
    }
}

/**
 * The colour picker, opened from the toolbar: a colour for the chosen tool (starting from its
 * own), kept among the recent ones; with the selection tool, it restyles the selection.
 */
@Composable
internal fun ToolColorPicker(
    controls: ToolControls,
    onRestyle: (InkColor?, PenSize?) -> Unit,
    onDone: () -> Unit,
) {
    val tools = controls.tools
    val current =
        when (tools.tool) {
            Tool.HIGHLIGHTER -> tools.highlighter.color
            Tool.TEXT -> tools.text.color
            Tool.PEN, Tool.ERASER, Tool.SELECT -> tools.pen.color
        }
    ColorPickerDialog(
        initial = current,
        onPick = {
            onDone()
            controls.onCustomColor(it)
            if (tools.tool == Tool.SELECT) onRestyle(it, null)
        },
        onDismiss = onDone,
    )
}

/** Fades the content out towards an edge it can be scrolled ([state]) past: there is more there. */
internal fun Modifier.fadingEdges(state: ScrollState): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val edge = FADE.toPx().coerceAtMost(size.width / 2)
        if (state.canScrollBackward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = edge),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollForward) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Black, Color.Transparent),
                    startX = size.width - edge,
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * A swatch or width's tap target: 40 × 48 dp around what is drawn (32 dp), with a round ripple,
 * selectable ([selected]) by [onClick].
 */
internal fun Modifier.tapTarget(
    selected: Boolean,
    onClick: () -> Unit,
): Modifier =
    size(width = TARGET_WIDTH, height = TARGET_HEIGHT).selectable(
        selected = selected,
        interactionSource = null,
        indication = ripple(bounded = false, radius = TARGET_WIDTH / 2),
        role = Role.RadioButton,
        onClick = onClick,
    )

private val TARGET_WIDTH = 40.dp
private val TARGET_HEIGHT = 48.dp
private val FADE = 32.dp
