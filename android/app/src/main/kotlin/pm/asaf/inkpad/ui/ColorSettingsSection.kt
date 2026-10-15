// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pm.asaf.inkpad.R
import pm.asaf.inkpad.settings.ColorPreferences

/** How many fixed colours the toolbar offers and which, and how many recent colours it keeps. */
@Composable
fun ColorSettingsSection(
    colors: ColorSettings,
    actions: SettingsActions,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    val fixedLabel = stringResource(R.string.fixed_colors)
    Stepper(fixedLabel, colors.fixed.size, 1..ColorPreferences.MAX_FIXED, actions.fixedCount)
    // Wraps on narrow screens.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        colors.fixed.forEachIndexed { index, color ->
            val description = stringResource(R.string.edit_color, colorName(color))
            Row(
                modifier =
                    Modifier
                        .size(36.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(3.dp)
                        .background(color.toCompose(), CircleShape)
                        .clickable(role = Role.Button) { editing = index }
                        .semantics { contentDescription = description },
            ) {}
        }
    }
    Stepper(
        stringResource(R.string.recent_colors_limit),
        colors.recentLimit,
        0..ColorPreferences.MAX_RECENT_LIMIT,
        actions.recentLimit,
    )
    editing?.let { index ->
        ColorPickerDialog(
            initial = colors.fixed[index],
            onPick = {
                editing = null
                actions.fixedColor(index, it)
            },
            onDismiss = { editing = null },
        )
    }
}

/** [label], with [value] and buttons to change it within [range]. */
@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(RemoveIcon, stringResource(R.string.fewer, label))
        }
        Text(value.toString(), textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 32.dp))
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(AddIcon, stringResource(R.string.more, label))
        }
    }
}
