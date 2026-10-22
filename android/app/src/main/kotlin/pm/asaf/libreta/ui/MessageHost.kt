// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pm.asaf.libreta.Messages

/** Where screens tell the user things (e.g. a change that can be undone). */
val LocalMessages = staticCompositionLocalOf<Messages?> { null }

/** Shows [messages] one after another in snackbars at the bottom, with their actions. */
@Composable
fun BoxScope.MessageHost(messages: Messages) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(messages) {
        messages.shown.collect { message ->
            val action = message.action
            val result =
                host.showSnackbar(
                    message.text,
                    actionLabel = action?.label,
                    withDismissAction = true,
                    // Long enough to reach for the action.
                    duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) action?.run?.invoke()
        }
    }
    SnackbarHost(host, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
}
