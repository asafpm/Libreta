// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import android.graphics.RectF
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pm.asaf.inkpad.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The selection's commands, floating just above [anchor] (the selection's box, in pixels of
 * the area this fills), or below it when there is no room above.
 */
@Composable
fun SelectionBar(
    anchor: RectF,
    actions: SelectionActions,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier =
            Modifier.layout { measurable, constraints ->
                val bar = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val gap = GAP_DP.dp.roundToPx()
                    val above = anchor.top - gap - bar.height
                    val y = if (above >= 0) above else anchor.bottom + gap
                    bar.place(
                        (anchor.centerX() - bar.width / 2f).roundToInt().coerceIn(
                            0,
                            max(
                                0,
                                constraints.maxWidth - bar.width,
                            ),
                        ),
                        y.roundToInt().coerceIn(0, max(0, constraints.maxHeight - bar.height)),
                    )
                }
            },
    ) {
        Row(Modifier.padding(horizontal = 4.dp)) {
            TextButton(onClick = actions.cut) { Text(stringResource(R.string.cut)) }
            TextButton(onClick = actions.copy) { Text(stringResource(R.string.copy)) }
            TextButton(onClick = actions.paste, enabled = actions.canPaste) { Text(stringResource(R.string.paste)) }
            TextButton(onClick = actions.duplicate) { Text(stringResource(R.string.duplicate)) }
            TextButton(onClick = actions.delete) { Text(stringResource(R.string.delete)) }
        }
    }
}

private const val GAP_DP = 12
