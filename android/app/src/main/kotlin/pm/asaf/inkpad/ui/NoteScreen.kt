// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.graphics.lowlatency.LowLatencyCanvasView
import pm.asaf.inkpad.NoteState
import pm.asaf.inkpad.R
import pm.asaf.inkpad.android.render.RenderThreads
import pm.asaf.inkpad.viewer.DocumentView
import pm.asaf.inkpad.viewer.LowLatencyWetInk

/**
 * A note, shown in a [DocumentView], with a notice when its background files could not be
 * found ([onAddFolder] asks for the note's folder when it is outside the library).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    name: String,
    state: NoteState?,
    threads: RenderThreads,
    position: ViewerPosition,
    onAddFolder: () -> Unit,
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
                    Column(Modifier.fillMaxSize()) {
                        FilesNotice(state, onAddFolder)
                        NoteCanvas(state, threads, position, { view = it }, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Why backgrounds are missing, if they are. */
@Composable
private fun FilesNotice(
    state: NoteState.Loaded,
    onAddFolder: () -> Unit,
) {
    val missing = state.files.missing
    if (!state.folderNeeded && missing.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val message =
                if (state.folderNeeded) {
                    stringResource(R.string.folder_needed)
                } else {
                    stringResource(R.string.missing_files, missing.joinToString())
                }
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (state.folderNeeded) {
                TextButton(onClick = onAddFolder) { Text(stringResource(R.string.add_this_folder)) }
            }
        }
    }
}

/** The pages, with wet ink in a front buffer above them; [onView] gets the view (null when gone). */
@Composable
private fun NoteCanvas(
    state: NoteState.Loaded,
    threads: RenderThreads,
    position: ViewerPosition,
    onView: (DocumentView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var document by remember { mutableStateOf<DocumentView?>(null) }
    AndroidView(
        factory = { context ->
            val view =
                DocumentView(context).apply {
                    val edits = state.edits
                    setDocument(edits.document.pages, state.caches, state.files::newRenderer, threads, edits.versions())
                    onPageEdited = edits::pageEdited
                    position.get()?.let { restore(it) }
                }
            val ink = LowLatencyCanvasView(context)
            view.wetInk = LowLatencyWetInk(ink)
            document = view
            onView(view)
            FrameLayout(context).apply {
                addView(view)
                addView(ink)
            }
        },
        onRelease = {
            document?.let { released ->
                position.set(released.anchor)
                released.close()
            }
            document = null
            onView(null)
        },
        modifier = modifier,
    )
}
