// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.app.UiModeManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import pm.asaf.libreta.settings.ThemeMode
import pm.asaf.libreta.viewer.PageRecolor

/** Whether pages are shown recoloured dark (PageRecolor). */
val LocalDarkPages = staticCompositionLocalOf { false }

/** The colour filter page pictures (thumbnails, previews) are drawn with: dark pages' recolouring, or none. */
@Composable
fun pageColorFilter(): ColorFilter? =
    if (LocalDarkPages.current) ColorFilter.colorMatrix(ColorMatrix(PageRecolor.matrix)) else null

/** Libreta's look: light or dark as [mode] says, with pages dark too if [darkPages]. */
@Composable
fun LibretaTheme(
    mode: ThemeMode,
    darkPages: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // The app's own night mode, so that the window and system bars follow too.
    LaunchedEffect(mode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val night =
                when (mode) {
                    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                }
            context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(night)
        }
    }
    val dark =
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalDarkPages provides (dark && darkPages), content = content)
    }
}
