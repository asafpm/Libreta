// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.PenSettings
import pm.asaf.inkpad.core.model.PenSize

/** The pen and how fingers act, remembered in [preferences]. */
class PenPreferences(
    private val preferences: SharedPreferences,
) {
    private val penState =
        MutableStateFlow(
            PenSettings(
                color =
                    preferences.getLong(COLOR_KEY, -1L).takeIf { it >= 0 }?.let { Color(it.toUInt()) }
                        ?: PenSettings().color,
                size =
                    PenSize.entries.firstOrNull { it.name == preferences.getString(SIZE_KEY, null) }
                        ?: PenSettings().size,
            ),
        )
    val pen: StateFlow<PenSettings> = penState.asStateFlow()

    private val fingerState = MutableStateFlow(preferences.getBoolean(FINGER_KEY, false))

    /** Whether fingers write (otherwise they pan and zoom). */
    val fingerDraws: StateFlow<Boolean> = fingerState.asStateFlow()

    fun setColor(color: Color) = setPen(penState.value.copy(color = color))

    fun setSize(size: PenSize) = setPen(penState.value.copy(size = size))

    private fun setPen(pen: PenSettings) {
        penState.value = pen
        preferences
            .edit()
            .putLong(COLOR_KEY, pen.color.rgba.toLong())
            .putString(SIZE_KEY, pen.size.name)
            .apply()
    }

    fun setFingerDraws(draws: Boolean) {
        fingerState.value = draws
        preferences.edit().putBoolean(FINGER_KEY, draws).apply()
    }

    private companion object {
        const val COLOR_KEY = "pen.color"
        const val SIZE_KEY = "pen.size"
        const val FINGER_KEY = "finger.draws"
    }
}
