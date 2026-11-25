// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PaperSize

/**
 * Chooses a paper size and orientation, starting from [page]'s, for that page or all
 * ([onApply]'s third argument). A page of no listed size keeps its own when only turned (a null
 * paper size). [hasPdfPages]: the note has pages that keep the PDF's size, as the dialog says.
 */
@Composable
fun PageSizeDialog(
    page: Page,
    hasPdfPages: Boolean,
    onApply: (paper: PaperSize?, landscape: Boolean, allPages: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var paper by remember { mutableStateOf(PaperSize.of(page.width, page.height)) }
    var landscape by remember { mutableStateOf(page.width > page.height) }
    var allPages by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PaperChoice(paper, landscape, { paper = it }) { landscape = it }
                AllPagesChoice(allPages) { allPages = it }
                val notes =
                    listOfNotNull(R.string.page_size_keeps_content, R.string.pdf_pages_keep_size.takeIf { hasPdfPages })
                for (note in notes) {
                    Text(
                        stringResource(note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(paper, landscape, allPages) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
