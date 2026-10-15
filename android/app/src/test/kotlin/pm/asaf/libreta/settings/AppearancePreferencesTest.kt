// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearancePreferencesTest {
    private val preferences =
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(
            "appearance-test",
            Context.MODE_PRIVATE,
        )

    @Test
    fun followsTheSystemWithDarkPagesUntilChanged() {
        preferences.edit().clear().commit()
        val appearance = AppearancePreferences(preferences)
        assertEquals(ThemeMode.SYSTEM, appearance.theme.value)
        assertEquals(true, appearance.darkPages.value)
        appearance.setTheme(ThemeMode.DARK)
        appearance.setDarkPages(false)
        val again = AppearancePreferences(preferences)
        assertEquals(ThemeMode.DARK, again.theme.value)
        assertEquals(false, again.darkPages.value)
    }
}
