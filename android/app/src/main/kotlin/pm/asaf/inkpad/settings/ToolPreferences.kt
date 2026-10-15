// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.EraserSettings
import pm.asaf.inkpad.core.model.EraserType
import pm.asaf.inkpad.core.model.HighlighterSettings
import pm.asaf.inkpad.core.model.PenSettings
import pm.asaf.inkpad.core.model.PenSize
import pm.asaf.inkpad.core.model.StylusButton
import pm.asaf.inkpad.core.model.Tool
import pm.asaf.inkpad.core.model.ToolSettings

/**
 * The tools as last used (the chosen one, and each one's colour and width), the stylus
 * button and how fingers act, remembered in [preferences].
 */
class ToolPreferences(
    private val preferences: SharedPreferences,
) {
    private val toolState = MutableStateFlow(preferences.readTools())
    val tools: StateFlow<ToolSettings> = toolState.asStateFlow()

    private val fingerState = MutableStateFlow(preferences.getBoolean(Keys.FINGER, false))

    /** Whether fingers write (otherwise they pan and zoom). */
    val fingerDraws: StateFlow<Boolean> = fingerState.asStateFlow()

    fun selectTool(tool: Tool) = update { it.copy(tool = tool) }

    /** Sets the colour of the chosen tool (the pen if it has none). */
    fun setColor(color: Color) =
        update {
            when (it.tool) {
                Tool.HIGHLIGHTER -> it.copy(highlighter = it.highlighter.copy(color = color))
                else -> it.copy(pen = it.pen.copy(color = color))
            }
        }

    /** Sets the width of the chosen tool. */
    fun setSize(size: PenSize) =
        update {
            when (it.tool) {
                Tool.PEN -> it.copy(pen = it.pen.copy(size = size))
                Tool.HIGHLIGHTER -> it.copy(highlighter = it.highlighter.copy(size = size))
                Tool.ERASER -> it.copy(eraser = it.eraser.copy(size = size))
            }
        }

    fun setEraserType(type: EraserType) = update { it.copy(eraser = it.eraser.copy(type = type)) }

    fun setButton(button: StylusButton) = update { it.copy(button = button) }

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
            .putLong(
                Keys.HIGHLIGHTER_COLOR,
                tools.highlighter.color.rgba
                    .toLong(),
            ).putString(Keys.HIGHLIGHTER_SIZE, tools.highlighter.size.name)
            .putString(Keys.ERASER_TYPE, tools.eraser.type.name)
            .putString(Keys.ERASER_SIZE, tools.eraser.size.name)
            .putString(Keys.BUTTON, tools.button.name)
            .apply()
    }
}

private fun SharedPreferences.readTools(): ToolSettings {
    val defaults = ToolSettings()
    return ToolSettings(
        tool = enumOf(Keys.TOOL, defaults.tool),
        pen = PenSettings(color(Keys.PEN_COLOR) ?: defaults.pen.color, enumOf(Keys.PEN_SIZE, defaults.pen.size)),
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
    )
}

private fun SharedPreferences.color(key: String): Color? =
    getLong(key, -1L)
        .takeIf {
            it >= 0
        }?.let { Color(it.toUInt()) }

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
    const val HIGHLIGHTER_COLOR = "highlighter.color"
    const val HIGHLIGHTER_SIZE = "highlighter.size"
    const val ERASER_TYPE = "eraser.type"
    const val ERASER_SIZE = "eraser.size"
    const val BUTTON = "stylus.button"
    const val FINGER = "finger.draws"
}
