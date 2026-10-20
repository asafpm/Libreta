// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.Palette
import pm.asaf.libreta.core.model.PenSize

/** The "?" button: what each button does, [topic]'s first. */
@Composable
fun HelpButton(topic: HelpTopic) {
    var open by remember { mutableStateOf(false) }
    TooltipIconButton(HelpIcon, stringResource(R.string.help)) { open = true }
    if (open) HelpDialog(topic) { open = false }
}

/** What each button does, [topic]'s part first. */
@Composable
fun HelpDialog(
    topic: HelpTopic,
    onDismiss: () -> Unit,
) {
    val sections = if (topic == HelpTopic.NOTE) NOTE + LIBRARY else LIBRARY + NOTE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.help)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (section in sections) {
                    item(key = section.title) {
                        Text(
                            stringResource(section.title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    for (entry in section.entries) {
                        item(key = entry.text) { HelpRow(entry) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun HelpRow(entry: HelpEntry) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            when (val look = entry.look) {
                is Look.Icon -> Icon(look.icon, contentDescription = null)
                Look.Colour -> Box(Modifier.size(24.dp).background(Palette.DEFAULT_PEN.toCompose(), CircleShape))
                Look.Width -> WidthDot(PenSize.THICK, MaterialTheme.colorScheme.onSurfaceVariant)
                Look.None -> Unit
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(entry.title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(entry.text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** How a button looks, to recognise it. */
private sealed interface Look {
    data class Icon(
        val icon: ImageVector,
    ) : Look

    data object Colour : Look

    data object Width : Look

    /** Not a button: a gesture. */
    data object None : Look
}

private class HelpEntry(
    val look: Look,
    val title: Int,
    val text: Int,
)

private class HelpSection(
    val title: Int,
    val entries: List<HelpEntry>,
)

private fun entry(
    icon: ImageVector,
    title: Int,
    text: Int,
) = HelpEntry(Look.Icon(icon), title, text)

private val NOTE =
    listOf(
        HelpSection(
            R.string.help_tools,
            listOf(
                entry(PenIcon, R.string.tool_pen, R.string.help_pen),
                entry(HighlighterIcon, R.string.tool_highlighter, R.string.help_highlighter),
                entry(EraserIcon, R.string.tool_eraser, R.string.help_eraser),
                entry(SelectIcon, R.string.tool_select, R.string.help_select),
                entry(TextIcon, R.string.tool_text, R.string.help_text),
            ),
        ),
        HelpSection(
            R.string.help_options,
            listOf(
                entry(FreehandIcon, R.string.help_shape_title, R.string.help_shape),
                HelpEntry(Look.Colour, R.string.colors, R.string.help_colors),
                HelpEntry(Look.Width, R.string.stroke_widths, R.string.help_widths),
                entry(PressureIcon, R.string.pen_pressure, R.string.help_pressure),
                entry(FingerIcon, R.string.draw_with_finger, R.string.help_finger),
            ),
        ),
        HelpSection(
            R.string.help_top_bar,
            listOf(
                entry(UndoIcon, R.string.help_undo_title, R.string.help_undo),
                entry(PagesIcon, R.string.pages, R.string.help_pages),
                entry(AddPageIcon, R.string.insert_page, R.string.help_insert_page),
                entry(LayersIcon, R.string.layers, R.string.help_layers),
                entry(PasteIcon, R.string.paste, R.string.help_paste),
                entry(ImageIcon, R.string.insert_image, R.string.help_image),
                entry(CameraIcon, R.string.take_photo, R.string.help_photo),
                entry(ExportIcon, R.string.help_export_title, R.string.help_export),
                HelpEntry(Look.None, R.string.fit_width, R.string.help_fit_width),
            ),
        ),
        HelpSection(
            R.string.help_input,
            listOf(
                HelpEntry(Look.None, R.string.help_button_title, R.string.help_button),
                HelpEntry(Look.None, R.string.help_eraser_end_title, R.string.help_eraser_end),
                HelpEntry(Look.None, R.string.help_hover_title, R.string.help_hover),
                HelpEntry(Look.None, R.string.help_fingers_title, R.string.help_fingers),
                HelpEntry(Look.None, R.string.help_keyboard_title, R.string.help_keyboard),
            ),
        ),
    )

private val LIBRARY =
    listOf(
        HelpSection(
            R.string.help_library,
            listOf(
                entry(NewFolderIcon, R.string.new_folder, R.string.help_new_folder),
                entry(Icons.Filled.Add, R.string.new_note, R.string.help_new_note),
                entry(Icons.Filled.MoreVert, R.string.help_card_menu_title, R.string.help_card_menu),
                HelpEntry(Look.None, R.string.help_drag_title, R.string.help_drag),
                HelpEntry(Look.None, R.string.help_pdf_title, R.string.help_pdf),
                entry(Icons.Filled.Settings, R.string.settings, R.string.help_settings),
                HelpEntry(Look.None, R.string.help_names_title, R.string.help_names),
            ),
        ),
    )
