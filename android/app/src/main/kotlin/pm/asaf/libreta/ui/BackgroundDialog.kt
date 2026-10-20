// SPDX-License-Identifier: GPL-2.0-or-later
// The paper colours are the original Xournal's (predef_bgcolors_rgba).

package pm.asaf.libreta.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.android.render.PageRenderer
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageStyle

/**
 * Chooses a page's ruling and paper colour, starting from [current], for that page or all
 * ([onApply]'s second argument).
 */
@Composable
fun BackgroundDialog(
    current: Background?,
    newRenderer: () -> PageRenderer,
    onApply: (Background.Solid, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val solid = current as? Background.Solid
    var style by remember {
        mutableStateOf(
            solid?.style?.takeIf { s ->
                STYLES.any { it.first == s }
            } ?: PageStyle.LINED,
        )
    }
    var color by remember { mutableStateOf(solid?.color ?: Color.WHITE) }
    var allPages by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val previews = rememberRulingPreviews(newRenderer)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_background)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ruling), style = MaterialTheme.typography.titleSmall)
                RulingChoices(style, previews) { style = it }
                Text(stringResource(R.string.paper_color), style = MaterialTheme.typography.titleSmall)
                PaperChoices(color, { color = it }) { picking = true }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.toggleable(value = allPages, role = Role.Checkbox) { allPages = it },
                ) {
                    Checkbox(checked = allPages, onCheckedChange = null)
                    Text(stringResource(R.string.apply_to_all_pages), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(Background.Solid(color, style), allPages) },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) {
        ColorPickerDialog(
            initial = color,
            onPick = {
                color = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** The rulings, as small samples ([previews]); [style] chosen. */
@Composable
internal fun RulingChoices(
    style: PageStyle,
    previews: Map<PageStyle, ImageBitmap>,
    onChoose: (PageStyle) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((ruling, label) in STYLES) {
            val selected = ruling == style
            val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .width(72.dp)
                        .selectable(selected = selected, role = Role.RadioButton) { onChoose(ruling) },
            ) {
                Image(
                    checkNotNull(previews[ruling]),
                    contentDescription = null,
                    colorFilter = pageColorFilter(),
                    modifier = Modifier.border(if (selected) 3.dp else 1.dp, ring),
                )
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** The paper colours, [color] chosen (shown too if it is none of them), and a picker ([onPick]). */
@Composable
internal fun PaperChoices(
    color: Color,
    onChoose: (Color) -> Unit,
    onPick: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val swatches = PAPER + listOfNotNull((color to 0).takeIf { (c, _) -> PAPER.none { it.first == c } })
        for ((paper, name) in swatches) {
            val description = if (name == 0) color.hex() else stringResource(name)
            val selected = paper == color
            val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            Row(
                modifier =
                    Modifier
                        .size(36.dp)
                        .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
                        .padding(4.dp)
                        .background(paper.toCompose(), CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton) { onChoose(paper) }
                        .semantics { contentDescription = description },
            ) {}
        }
        AddColorButton(onPick)
    }
}

/** Small samples of each ruling, drawn as pages are. */
@Composable
internal fun rememberRulingPreviews(newRenderer: () -> PageRenderer): Map<PageStyle, ImageBitmap> =
    remember {
        val renderer = newRenderer()
        STYLES.associate { (ruling, _) ->
            val sample =
                Page(SAMPLE_WIDTH, SAMPLE_HEIGHT, Background.Solid(Color.WHITE, ruling), listOf(Layer(emptyList())))
            ruling to renderer.renderToBitmap(sample, PREVIEW_ZOOM).asImageBitmap()
        }
    }

/** Xournal++'s rulings, with names. */
private val STYLES =
    listOf(
        PageStyle.PLAIN to R.string.style_plain,
        PageStyle.RULED to R.string.style_ruled,
        PageStyle.LINED to R.string.style_lined,
        PageStyle.STAVES to R.string.style_staves,
        PageStyle.GRAPH to R.string.style_graph,
        PageStyle.DOTTED to R.string.style_dotted,
        PageStyle.ISO_DOTTED to R.string.style_isodotted,
        PageStyle.ISO_GRAPH to R.string.style_isograph,
    )

/** The original Xournal's paper colours. */
private val PAPER =
    listOf(
        Color.WHITE to R.string.paper_white,
        Color.of(0xa0, 0xe8, 0xff) to R.string.paper_blue,
        Color.of(0xff, 0xc0, 0xd4) to R.string.paper_pink,
        Color.of(0xff, 0xc0, 0x80) to R.string.paper_orange,
        Color.of(0xff, 0xff, 0x80) to R.string.paper_yellow,
        Color.of(0x80, 0xff, 0xc0) to R.string.paper_green,
    )

/** A corner of an A4 page, enough to see the ruling. */
private const val SAMPLE_WIDTH = 200.0
private const val SAMPLE_HEIGHT = 260.0
private const val PREVIEW_ZOOM = 0.9f
