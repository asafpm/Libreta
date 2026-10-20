// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pm.asaf.libreta.R

/**
 * [content] (a button showing only an icon) with its [label] in a tooltip: shown on a long
 * press, while a mouse hovers over it, and while the stylus does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(
    label: String,
    content: @Composable () -> Unit,
) {
    val state = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = state,
        // Material's tooltips follow a hovering mouse but not a hovering stylus.
        modifier =
            Modifier.pointerInput(state) {
                var showing: Job? = null
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.firstOrNull()?.type != PointerType.Stylus) continue
                        when (event.type) {
                            PointerEventType.Enter -> {
                                showing?.cancel()
                                showing =
                                    scope.launch {
                                        // Not for a stylus passing by.
                                        delay(HOVER_MS)
                                        state.show()
                                    }
                            }

                            PointerEventType.Exit, PointerEventType.Press -> {
                                showing?.cancel()
                                state.dismiss()
                            }
                        }
                    }
                }
            },
    ) { content() }
}

private const val HOVER_MS = 400L

/** A button showing just [icon], named [label] (for accessibility and in its tooltip). */
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    WithTooltip(label) { IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label) } }
}

/** Back, as the arrow at the start of a top bar. */
@Composable
fun BackButton(onBack: () -> Unit) =
    TooltipIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onClick = onBack)
