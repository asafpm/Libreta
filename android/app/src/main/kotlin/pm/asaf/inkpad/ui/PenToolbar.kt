// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pm.asaf.inkpad.R
import pm.asaf.inkpad.core.model.Palette
import pm.asaf.inkpad.core.model.PenSize
import pm.asaf.inkpad.core.model.Color as InkColor

/** Pen colour (Xournal++'s default pen colour and palette) and width, and finger drawing. */
@Composable
fun PenToolbar(
    controls: PenControls,
    modifier: Modifier = Modifier,
) {
    val pen = controls.pen
    val fingerDraws = controls.fingerDraws
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            val colors = SWATCHES.map { it.first }
            val custom = if (pen.color in colors) emptyList() else listOf(pen.color to R.string.color_custom)
            for ((color, name) in SWATCHES + custom) {
                Swatch(color, stringResource(name), selected = color == pen.color) { controls.onColor(color) }
            }
            VerticalDivider(Modifier.padding(horizontal = 6.dp).size(width = 1.dp, height = 28.dp))
            for (size in PenSize.entries) {
                WidthDot(size, pen.color, selected = size == pen.size) { controls.onSize(size) }
            }
            VerticalDivider(Modifier.padding(horizontal = 6.dp).size(width = 1.dp, height = 28.dp))
            FilterChip(
                selected = fingerDraws,
                onClick = { controls.onFingerDraws(!fingerDraws) },
                label = { Text(stringResource(R.string.draw_with_finger)) },
            )
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun Swatch(
    color: InkColor,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.pen_color, name)
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

@Composable
private fun WidthDot(
    size: PenSize,
    color: InkColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.pen_width, stringResource(size.label))
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
        // Thin widths stay visible.
        val diameter = (size.width * DOT_SCALE).coerceIn(MIN_DOT, MAX_DOT).dp
        Box(Modifier.size(diameter).background(color.toCompose(), CircleShape))
    }
}

private fun InkColor.toCompose() = Color(red, green, blue, alpha)

private val PenSize.label: Int
    get() =
        when (this) {
            PenSize.VERY_FINE -> R.string.width_very_fine
            PenSize.FINE -> R.string.width_fine
            PenSize.MEDIUM -> R.string.width_medium
            PenSize.THICK -> R.string.width_thick
            PenSize.VERY_THICK -> R.string.width_very_thick
        }

/** Xournal++'s default pen colour, then its palette, with names. */
private val SWATCHES: List<Pair<InkColor, Int>> =
    listOf(Palette.DEFAULT_PEN to R.string.color_royal_blue) +
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

private const val DOT_SCALE = 3.0
private const val MIN_DOT = 3.0
private const val MAX_DOT = 20.0
