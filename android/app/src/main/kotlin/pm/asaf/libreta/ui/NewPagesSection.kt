// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.android.render.PageRenderer
import pm.asaf.libreta.core.model.NewPageTemplate

/**
 * How new pages look: like the page before (Xournal++'s default), or [template]'s paper,
 * ruling and colour, which new notes always use.
 */
@Composable
fun NewPagesSection(
    template: NewPageTemplate,
    onChange: (NewPageTemplate) -> Unit,
) {
    val context = LocalContext.current
    val previews = rememberRulingPreviews { PageRenderer(context) }
    var picking by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.copy_current_page)) },
        supportingContent = { Text(stringResource(R.string.copy_current_page_summary)) },
        trailingContent = { Switch(checked = template.copyCurrent, onCheckedChange = null) },
        modifier =
            Modifier.toggleable(value = template.copyCurrent, role = Role.Switch) {
                onChange(template.copy(copyCurrent = it))
            },
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(if (template.copyCurrent) R.string.template_for_notes else R.string.template_for_pages),
            style = MaterialTheme.typography.bodyMedium,
        )
        PaperChoice(template.paper, template.landscape, { onChange(template.copy(paper = it)) }) {
            onChange(template.copy(landscape = it))
        }
        Text(stringResource(R.string.ruling), style = MaterialTheme.typography.labelLarge)
        RulingChoices(template.style, previews) { onChange(template.copy(style = it)) }
        Text(stringResource(R.string.paper_color), style = MaterialTheme.typography.labelLarge)
        PaperChoices(template.color, { onChange(template.copy(color = it)) }) { picking = true }
    }
    if (picking) {
        ColorPickerDialog(
            initial = template.color,
            onPick = {
                picking = false
                onChange(template.copy(color = it))
            },
            onDismiss = { picking = false },
        )
    }
}
