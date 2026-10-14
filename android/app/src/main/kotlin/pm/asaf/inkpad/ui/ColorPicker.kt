// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pm.asaf.inkpad.R
import pm.asaf.inkpad.core.model.Color as InkColor

/**
 * Picks an opaque colour, starting from [initial]: saturation and brightness in a square,
 * hue in a bar below, or a hex code.
 */
@Composable
fun ColorPickerDialog(
    initial: InkColor,
    onPick: (InkColor) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = remember { initial.toHsv() }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val picked = hsvColor(hue, saturation, value)
    var hex by remember { mutableStateOf(picked.hex()) }

    fun pick(
        h: Float = hue,
        s: Float = saturation,
        v: Float = value,
    ) {
        hue = h
        saturation = s
        value = v
        hex = hsvColor(h, s, v).hex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SaturationValue(hue, saturation, value) { s, v -> pick(s = s, v = v) }
                HueBar(hue) { pick(h = it) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val outline = MaterialTheme.colorScheme.outline
                    Canvas(Modifier.size(40.dp).border(1.dp, outline, CircleShape)) { drawCircle(picked.toCompose()) }
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { text ->
                            hex = text
                            parseHex(text)?.toHsv()?.let { (h, s, v) ->
                                hue = h
                                saturation = s
                                value = v
                            }
                        },
                        label = { Text(stringResource(R.string.color_hex)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.testTag("hex"),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(picked) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Saturation left to right, brightness bottom to top, at [hue]. */
@Composable
private fun SaturationValue(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    val description = stringResource(R.string.color_saturation_value)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                fun at(offset: Offset) =
                    onChange(
                        (offset.x / size.width).coerceIn(0f, 1f),
                        1f - (offset.y / size.height).coerceIn(0f, 1f),
                    )
                detectTapGestures { at(it) }
            }.pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onChange(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        1f - (change.position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hsvColor(hue, 1f, 1f).toCompose())))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = MARKER_RADIUS.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, radius = MARKER_RADIUS.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
    }
}

/** Hues from red round to red. */
@Composable
private fun HueBar(
    hue: Float,
    onChange: (Float) -> Unit,
) {
    val description = stringResource(R.string.color_hue)
    val hues = remember { (0..HUE_STOPS).map { hsvColor(it * FULL_TURN / HUE_STOPS, 1f, 1f).toCompose() } }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f) * FULL_TURN) }
            }.pointerInput(Unit) {
                detectDragGestures {
                    change,
                    _,
                    ->
                    onChange((change.position.x / size.width).coerceIn(0f, 1f) * FULL_TURN)
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(hues))
        val x = hue / FULL_TURN * size.width
        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4.dp.toPx())
        drawLine(Color.Black, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
    }
}

private fun InkColor.toHsv(): FloatArray =
    FloatArray(
        HSV_COMPONENTS,
    ).also { android.graphics.Color.colorToHSV(android.graphics.Color.rgb(red, green, blue), it) }

private fun hsvColor(
    hue: Float,
    saturation: Float,
    value: Float,
): InkColor {
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    return InkColor.of(
        android.graphics.Color.red(argb),
        android.graphics.Color.green(argb),
        android.graphics.Color.blue(argb),
    )
}

/** "#rrggbb". */
internal fun InkColor.hex(): String = "#%02x%02x%02x".format(red, green, blue)

/** An opaque colour from "#rrggbb" or "rrggbb", or null. */
private fun parseHex(text: String): InkColor? =
    text
        .trim()
        .removePrefix("#")
        .takeIf { it.length == HEX_DIGITS }
        ?.toIntOrNull(HEX)
        ?.let { rgb -> InkColor.of(rgb shr RED_SHIFT and BYTE, rgb shr GREEN_SHIFT and BYTE, rgb and BYTE) }

internal fun InkColor.toCompose() = Color(red, green, blue, alpha)

private const val MARKER_RADIUS = 8
private const val HUE_STOPS = 12
private const val FULL_TURN = 360f
private const val HEX_DIGITS = 6
private const val HSV_COMPONENTS = 3
private const val HEX = 16
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTE = 0xff
