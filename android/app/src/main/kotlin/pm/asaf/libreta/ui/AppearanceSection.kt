// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.settings.ThemeMode

/** The theme (as the system, light, dark), and whether pages are dark with it. */
@Composable
fun AppearanceSection(appearance: AppearanceSettings) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        for ((mode, label) in THEMES) {
            FilterChip(
                selected = appearance.theme == mode,
                onClick = { appearance.onTheme(mode) },
                label = { Text(stringResource(label)) },
            )
        }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.dark_pages)) },
        supportingContent = { Text(stringResource(R.string.dark_pages_summary)) },
        trailingContent = { Switch(checked = appearance.darkPages, onCheckedChange = null) },
        modifier =
            Modifier.toggleable(
                value = appearance.darkPages,
                role = Role.Switch,
                onValueChange = appearance.onDarkPages,
            ),
    )
}

private val THEMES =
    listOf(
        ThemeMode.SYSTEM to R.string.theme_system,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark,
    )
