// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import pm.asaf.libreta.R
import pm.asaf.libreta.core.geometry.FontDescription
import pm.asaf.libreta.core.geometry.FontFamily
import pm.asaf.libreta.core.model.TextSettings

/** The text tool's font (family, bold, italic), size and colour. */
@Composable
fun RowScope.TextOptions(
    controls: ToolControls,
    onAddColor: () -> Unit,
) {
    val text = controls.tools.text
    val font = FontDescription.parse(text.font)
    val family = FAMILIES.firstOrNull { it.second.contains(font.family) }?.first ?: FAMILIES.first().first
    val change = { style: TextSettings -> controls.onChange { it.copy(text = style) } }

    fun font(
        name: String = family,
        bold: Boolean = font.bold,
        italic: Boolean = font.italic,
    ) = listOfNotNull(name, BOLD.takeIf { bold }, ITALIC.takeIf { italic }).joinToString(" ")
    for ((name, _, label) in FAMILIES) {
        FilterChip(
            selected = family == name,
            onClick = { change(text.copy(font = font(name))) },
            label = { Text(stringResource(label)) },
        )
    }
    FilterChip(
        selected = font.bold,
        onClick = { change(text.copy(font = font(bold = !font.bold))) },
        label = { Text(stringResource(R.string.bold)) },
    )
    FilterChip(
        selected = font.italic,
        onClick = { change(text.copy(font = font(italic = !font.italic))) },
        label = { Text(stringResource(R.string.italic)) },
    )
    TextSizes(text.size) { change(text.copy(size = it)) }
    Separator()
    val swatches = (controls.fixedColors + controls.recentColors + text.color).distinct()
    for (swatch in swatches) {
        Swatch(swatch, colorName(swatch), selected = swatch == text.color) { controls.onColor(swatch) }
    }
    AddColorButton(onAddColor)
}

/** The text size, as a button opening the sizes offered. */
@Composable
private fun TextSizes(
    size: Double,
    onSize: (Double) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.text_size, points(size))) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in SIZES) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.text_size, points(option))) },
                    onClick = {
                        open = false
                        onSize(option)
                    },
                )
            }
        }
    }
}

private fun points(size: Double): String = if (size % 1.0 == 0.0) size.toInt().toString() else size.toString()

/** The families offered: the name written in files, the bundled families it covers, and its label. */
private val FAMILIES =
    listOf(
        Triple("Sans", setOf(FontFamily.DEJAVU_SANS, FontFamily.LIBERATION_SANS), R.string.font_sans),
        Triple("Serif", setOf(FontFamily.DEJAVU_SERIF, FontFamily.LIBERATION_SERIF), R.string.font_serif),
        Triple("Monospace", setOf(FontFamily.DEJAVU_SANS_MONO, FontFamily.LIBERATION_MONO), R.string.font_mono),
    )

private const val BOLD = "Bold"
private const val ITALIC = "Italic"

/** Text sizes offered, in points. */
private val SIZES = listOf(8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 24.0, 32.0, 36.0)
