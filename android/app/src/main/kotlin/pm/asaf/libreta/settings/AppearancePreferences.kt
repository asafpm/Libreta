// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the app is light or dark: as the system is, or always one (like Xournal++'s theme setting). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How the app looks, kept in [preferences]: its theme, and whether pages are shown dark with it. */
class AppearancePreferences(
    private val preferences: SharedPreferences,
) {
    private val themeState =
        MutableStateFlow(
            ThemeMode.entries.firstOrNull { it.name == preferences.getString(THEME_KEY, null) } ?: ThemeMode.SYSTEM,
        )
    val theme: StateFlow<ThemeMode> = themeState.asStateFlow()

    private val darkPagesState = MutableStateFlow(preferences.getBoolean(DARK_PAGES_KEY, true))

    /** Whether pages are recoloured dark while the app is dark. */
    val darkPages: StateFlow<Boolean> = darkPagesState.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        themeState.value = mode
        preferences.edit().putString(THEME_KEY, mode.name).apply()
    }

    fun setDarkPages(dark: Boolean) {
        darkPagesState.value = dark
        preferences.edit().putBoolean(DARK_PAGES_KEY, dark).apply()
    }

    private companion object {
        const val THEME_KEY = "appearance.theme"
        const val DARK_PAGES_KEY = "appearance.darkPages"
    }
}
