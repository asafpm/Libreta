// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.EraserSettings
import pm.asaf.libreta.core.model.EraserType
import pm.asaf.libreta.core.model.HighlighterSettings
import pm.asaf.libreta.core.model.PenSettings
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.SelectMode
import pm.asaf.libreta.core.model.StylusButton
import pm.asaf.libreta.core.model.TextSettings
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.ToolSettings

/**
 * The tools as last used (the chosen one, and each one's colour and width), the stylus
 * button, the widths and finger button the toolbar offers, and how fingers act, remembered in
 * [preferences].
 */
class ToolPreferences(
    private val preferences: SharedPreferences,
) {
    private val toolState = MutableStateFlow(preferences.readTools())
    val tools: StateFlow<ToolSettings> = toolState.asStateFlow()

    private val sizesState = MutableStateFlow(preferences.readSizes())

    /** The widths the toolbar offers, for every tool (never none). */
    val visibleSizes: StateFlow<Set<PenSize>> = sizesState.asStateFlow()

    private val fingerState = MutableStateFlow(preferences.getBoolean(Keys.FINGER, false))

    /** Whether fingers write (otherwise they pan and zoom). */
    val fingerDraws: StateFlow<Boolean> = fingerState.asStateFlow()

    private val fingerButtonState = MutableStateFlow(preferences.getBoolean(Keys.FINGER_BUTTON, true))

    /** Whether the toolbar has the "Draw with finger" button. */
    val fingerButton: StateFlow<Boolean> = fingerButtonState.asStateFlow()

    fun selectTool(tool: Tool) = update { it.copy(tool = tool) }

    /** Sets the colour of the chosen tool (the pen if it has none). */
    fun setColor(color: Color) =
        update {
            when (it.tool) {
                Tool.HIGHLIGHTER -> it.copy(highlighter = it.highlighter.copy(color = color))

                Tool.PEN, Tool.ERASER -> it.copy(pen = it.pen.copy(color = color))

                Tool.TEXT -> it.copy(text = it.text.copy(color = color))

                // The selection's colour changes instead.
                Tool.SELECT -> it
            }
        }

    /** Sets the width of the chosen tool. */
    fun setSize(size: PenSize) =
        update {
            when (it.tool) {
                Tool.PEN -> it.copy(pen = it.pen.copy(size = size))
                Tool.HIGHLIGHTER -> it.copy(highlighter = it.highlighter.copy(size = size))
                Tool.ERASER -> it.copy(eraser = it.eraser.copy(size = size))
                Tool.SELECT, Tool.TEXT -> it
            }
        }

    /** Changes the settings in a way that needs nothing more, e.g. the drawing type or text's font. */
    fun change(change: (ToolSettings) -> ToolSettings) = update(change)

    fun setEraserType(type: EraserType) = update { it.copy(eraser = it.eraser.copy(type = type)) }

    fun setButton(button: StylusButton) = update { it.copy(button = button) }

    fun setSelectMode(mode: SelectMode) = update { it.copy(selectMode = mode) }

    /** Shows width [size] in the toolbar, or hides it unless it is the last one shown. */
    fun setSizeVisible(
        size: PenSize,
        visible: Boolean,
    ) {
        val sizes = if (visible) sizesState.value + size else sizesState.value - size
        if (sizes.isEmpty()) return
        sizesState.value = sizes
        preferences
            .edit()
            .putString(
                Keys.SIZES,
                PenSize.entries.filter { it in sizes }.joinToString(",") { it.name },
            ).apply()
    }

    /** Shows the "Draw with finger" button, or hides it: then fingers no longer draw (it couldn't be undone). */
    fun setFingerButton(shown: Boolean) {
        fingerButtonState.value = shown
        preferences.edit().putBoolean(Keys.FINGER_BUTTON, shown).apply()
        if (!shown) setFingerDraws(false)
    }

    fun setFingerDraws(draws: Boolean) {
        fingerState.value = draws
        preferences.edit().putBoolean(Keys.FINGER, draws).apply()
    }

    private inline fun update(change: (ToolSettings) -> ToolSettings) {
        val tools = change(toolState.value)
        toolState.value = tools
        preferences
            .edit()
            .putString(Keys.TOOL, tools.tool.name)
            .putLong(
                Keys.PEN_COLOR,
                tools.pen.color.rgba
                    .toLong(),
            ).putString(Keys.PEN_SIZE, tools.pen.size.name)
            .putBoolean(Keys.PEN_PRESSURE, tools.pen.pressure)
            .putLong(
                Keys.HIGHLIGHTER_COLOR,
                tools.highlighter.color.rgba
                    .toLong(),
            ).putString(Keys.HIGHLIGHTER_SIZE, tools.highlighter.size.name)
            .putString(Keys.ERASER_TYPE, tools.eraser.type.name)
            .putString(Keys.ERASER_SIZE, tools.eraser.size.name)
            .putString(Keys.BUTTON, tools.button.name)
            .putString(Keys.SELECT_MODE, tools.selectMode.name)
            .putString(Keys.DRAWING_TYPE, tools.drawingType.name)
            .putString(Keys.TEXT_FONT, tools.text.font)
            .putString(Keys.TEXT_SIZE, tools.text.size.toString())
            .putLong(
                Keys.TEXT_COLOR,
                tools.text.color.rgba
                    .toLong(),
            ).apply()
    }
}

private fun SharedPreferences.readTools(): ToolSettings {
    val defaults = ToolSettings()
    return ToolSettings(
        tool = enumOf(Keys.TOOL, defaults.tool),
        pen =
            PenSettings(
                color(Keys.PEN_COLOR) ?: defaults.pen.color,
                enumOf(Keys.PEN_SIZE, defaults.pen.size),
                getBoolean(Keys.PEN_PRESSURE, defaults.pen.pressure),
            ),
        highlighter =
            HighlighterSettings(
                color(Keys.HIGHLIGHTER_COLOR) ?: defaults.highlighter.color,
                enumOf(Keys.HIGHLIGHTER_SIZE, defaults.highlighter.size),
            ),
        eraser =
            EraserSettings(
                enumOf(Keys.ERASER_TYPE, defaults.eraser.type),
                enumOf(Keys.ERASER_SIZE, defaults.eraser.size),
            ),
        button = enumOf(Keys.BUTTON, defaults.button),
        selectMode = enumOf(Keys.SELECT_MODE, defaults.selectMode),
        drawingType = enumOf(Keys.DRAWING_TYPE, defaults.drawingType),
        text =
            TextSettings(
                getString(Keys.TEXT_FONT, null) ?: defaults.text.font,
                getString(Keys.TEXT_SIZE, null)?.toDoubleOrNull() ?: defaults.text.size,
                color(Keys.TEXT_COLOR) ?: defaults.text.color,
            ),
    )
}

private fun SharedPreferences.color(key: String): Color? =
    getLong(key, -1L)
        .takeIf {
            it >= 0
        }?.let { Color(it.toUInt()) }

/** The widths stored as shown, all by default. */
private fun SharedPreferences.readSizes(): Set<PenSize> =
    getString(Keys.SIZES, null)
        ?.split(',')
        ?.mapNotNull { name -> PenSize.entries.firstOrNull { it.name == name } }
        ?.toSet()
        ?.takeIf { it.isNotEmpty() }
        ?: PenSize.entries.toSet()

private inline fun <reified T : Enum<T>> SharedPreferences.enumOf(
    key: String,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == getString(key, null) } ?: default

/** Preference keys. */
private object Keys {
    const val TOOL = "tool"

    // Kept from the pen-only toolbar.
    const val PEN_COLOR = "pen.color"
    const val PEN_SIZE = "pen.size"
    const val PEN_PRESSURE = "pen.pressure"
    const val HIGHLIGHTER_COLOR = "highlighter.color"
    const val HIGHLIGHTER_SIZE = "highlighter.size"
    const val ERASER_TYPE = "eraser.type"
    const val ERASER_SIZE = "eraser.size"
    const val BUTTON = "stylus.button"
    const val SELECT_MODE = "select.mode"
    const val DRAWING_TYPE = "drawing.type"
    const val TEXT_FONT = "text.font"
    const val TEXT_SIZE = "text.size"
    const val TEXT_COLOR = "text.color"
    const val FINGER = "finger.draws"
    const val FINGER_BUTTON = "toolbar.fingerButton"
    const val SIZES = "sizes.visible"
}
