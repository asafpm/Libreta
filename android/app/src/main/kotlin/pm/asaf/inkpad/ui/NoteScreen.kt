// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import pm.asaf.inkpad.NoteState
import pm.asaf.inkpad.R
import pm.asaf.inkpad.android.render.BackgroundSource
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.android.render.RenderThreads
import pm.asaf.inkpad.core.geometry.ViewportAnchor
import pm.asaf.inkpad.viewer.DocumentView

/** A note, shown in a [DocumentView]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    name: String,
    state: NoteState?,
    backgrounds: BackgroundSource,
    threads: RenderThreads,
    position: ViewerPosition,
    onBack: () -> Unit,
) {
    var view by remember { mutableStateOf<DocumentView?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(
                        onClick = { view?.fitWidth() },
                        enabled = view != null,
                    ) { Text(stringResource(R.string.fit_width)) }
                },
            )
        },
    ) { padding ->
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                null, NoteState.Loading -> {
                    CircularProgressIndicator()
                }

                is NoteState.Failed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                }

                is NoteState.Loaded -> {
                    AndroidView(
                        factory = { context ->
                            DocumentView(context).apply {
                                val appContext = context.applicationContext
                                setDocument(
                                    state.document.pages,
                                    state.caches,
                                    { PageRenderer(appContext, backgrounds) },
                                    threads,
                                )
                                position.get()?.let { restore(it) }
                                view = this
                            }
                        },
                        onRelease = { released ->
                            position.set(released.anchor)
                            released.close()
                            view = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
