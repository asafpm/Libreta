// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Palette

/**
 * The toolbar's colours for the pen and the highlighter: the fixed ones (Settings) and the
 * recent custom ones, at most [recentLimit] of them, remembered in [preferences].
 */
class ColorPreferences(
    private val preferences: SharedPreferences,
) {
    private val fixedState = MutableStateFlow(preferences.colors(ColorKeys.FIXED) ?: Palette.DEFAULT_FIXED)

    /** The colours the toolbar always offers, for the pen and the highlighter. */
    val fixedColors: StateFlow<List<Color>> = fixedState.asStateFlow()

    private val limitState =
        MutableStateFlow(preferences.getInt(ColorKeys.RECENT_LIMIT, DEFAULT_RECENT_LIMIT).coerceIn(0, MAX_RECENT_LIMIT))

    /** How many recent custom colours are kept. */
    val recentLimit: StateFlow<Int> = limitState.asStateFlow()

    private val recentState =
        MutableStateFlow(
            preferences
                .colors(ColorKeys.RECENT)
                .orEmpty()
                .filter { it !in fixedState.value }
                .take(limitState.value),
        )

    /** Custom colours picked, most recent first. */
    val recentColors: StateFlow<List<Color>> = recentState.asStateFlow()

    /** Remembers [color], picked from the colour picker, among the recent ones (unless it is a fixed one). */
    fun addRecentColor(color: Color) {
        if (color !in fixedState.value) setRecent(listOf(color) + recentState.value.filter { it != color })
    }

    /** Offers [count] fixed colours (1 to [MAX_FIXED]): more come from Xournal++'s palette, fewer drop the last. */
    fun setFixedCount(count: Int) {
        val fixed = fixedState.value
        val target = count.coerceIn(1, MAX_FIXED)
        val more = (Palette.DEFAULT_FIXED + Palette.XOURNALPP).distinct().filter { it !in fixed }
        setFixed((fixed + more).take(target))
    }

    /** Makes fixed colour [index] [color]. */
    fun setFixedColor(
        index: Int,
        color: Color,
    ) {
        val fixed = fixedState.value
        if (index in fixed.indices) setFixed(fixed.toMutableList().also { it[index] = color })
    }

    /** Keeps at most [limit] recent colours (0 to [MAX_RECENT_LIMIT]). */
    fun setRecentLimit(limit: Int) {
        limitState.value = limit.coerceIn(0, MAX_RECENT_LIMIT)
        preferences.edit().putInt(ColorKeys.RECENT_LIMIT, limitState.value).apply()
        setRecent(recentState.value)
    }

    private fun setFixed(fixed: List<Color>) {
        fixedState.value = fixed
        preferences.edit().putString(ColorKeys.FIXED, fixed.encode()).apply()
        // A colour now fixed needn't be recent too.
        setRecent(recentState.value)
    }

    private fun setRecent(colors: List<Color>) {
        val recent = colors.filter { it !in fixedState.value }.take(limitState.value)
        recentState.value = recent
        preferences.edit().putString(ColorKeys.RECENT, recent.encode()).apply()
    }

    companion object {
        const val MAX_FIXED = 12
        const val MAX_RECENT_LIMIT = 10
        const val DEFAULT_RECENT_LIMIT = 3
    }
}

/** The colours stored under [key], or null if none were. */
private fun SharedPreferences.colors(key: String): List<Color>? =
    getString(key, null)?.split(',')?.mapNotNull { it.toUIntOrNull(ColorKeys.HEX)?.let(::Color) }

private fun List<Color>.encode(): String = joinToString(",") { it.rgba.toString(ColorKeys.HEX) }

/** Preference keys. */
private object ColorKeys {
    // Kept from before the fixed colours.
    const val RECENT = "colors.recent"
    const val FIXED = "colors.fixed"
    const val RECENT_LIMIT = "colors.recentLimit"
    const val HEX = 16
}
