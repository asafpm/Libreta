// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.viewer.Navigation
import kotlin.math.roundToInt

/**
 * Over the note, at the bottom: the page in view of [count] ([current], from 0), opening a
 * "Go to page" field, and the zoom ([zoom] pixels a point), opening fit width, fit page and
 * 100% (real size, as Xournal++'s). [navigate] moves the view.
 */
@Composable
fun BoxScope.PageAndZoom(
    current: Int,
    count: Int,
    zoom: Double,
    navigate: (Navigation) -> Unit,
) {
    var going by remember { mutableStateOf(false) }
    var zooming by remember { mutableStateOf(false) }
    // A point is 1/72 inch: at 100% it is that big on the screen.
    val realSize = LocalResources.current.displayMetrics.xdpi / POINTS_PER_INCH
    val colors =
        SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
    ) {
        SuggestionChip(
            onClick = { going = true },
            label = { Text(stringResource(R.string.page_of, current + 1, count)) },
            colors = colors,
        )
        Box {
            SuggestionChip(
                onClick = { zooming = true },
                label = { Text(stringResource(R.string.zoom_percent, (zoom / realSize * PERCENT).roundToInt())) },
                colors = colors,
            )
            DropdownMenu(expanded = zooming, onDismissRequest = { zooming = false }) {
                val choices =
                    listOf(
                        R.string.fit_width to Navigation.FitWidth,
                        R.string.fit_page to Navigation.FitPage,
                        R.string.real_size to Navigation.ZoomTo(realSize.toDouble()),
                    )
                for ((label, move) in choices) {
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            zooming = false
                            navigate(move)
                        },
                    )
                }
            }
        }
    }
    if (going) {
        GoToPageDialog(count, onGo = { page ->
            going = false
            navigate(Navigation.ToPage(page))
        }) { going = false }
    }
}

/** Asks which of [count] pages to go to; [onGo] gets its index (from 0). */
@Composable
private fun GoToPageDialog(
    count: Int,
    onGo: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var number by rememberSaveable { mutableStateOf("") }
    val page = number.trim().toIntOrNull()?.takeIf { it in 1..count }
    val go = { page?.let { onGo(it - 1) } }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.go_to_page)) },
        text = {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.page_number_of, count)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { go() },
                enabled = page != null,
            ) { Text(stringResource(R.string.go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

private const val POINTS_PER_INCH = 72f
private const val PERCENT = 100
