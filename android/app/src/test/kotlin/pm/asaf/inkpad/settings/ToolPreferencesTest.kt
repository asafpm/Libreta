// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.inkpad.core.model.PenSize

@RunWith(AndroidJUnit4::class)
class ToolPreferencesTest {
    private val preferences =
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun choosesTheWidthsShown() {
        val tools = ToolPreferences(preferences)
        assertEquals(PenSize.entries.toSet(), tools.visibleSizes.value)
        for (size in listOf(
            PenSize.VERY_FINE,
            PenSize.MEDIUM,
            PenSize.VERY_THICK,
            PenSize.FINE,
        )) {
            tools.setSizeVisible(size, false)
        }
        assertEquals(setOf(PenSize.THICK), tools.visibleSizes.value)
        // Never none.
        tools.setSizeVisible(PenSize.THICK, false)
        assertEquals(setOf(PenSize.THICK), tools.visibleSizes.value)
        tools.setSizeVisible(PenSize.FINE, true)
        assertEquals(setOf(PenSize.FINE, PenSize.THICK), ToolPreferences(preferences).visibleSizes.value)
    }

    @Test
    fun hidingTheFingerButtonStopsFingerDrawing() {
        val tools = ToolPreferences(preferences)
        assertEquals(true, tools.fingerButton.value)
        tools.setFingerDraws(true)
        tools.setFingerButton(false)
        assertEquals(false, tools.fingerDraws.value)
        val again = ToolPreferences(preferences)
        assertEquals(false, again.fingerButton.value)
        assertEquals(false, again.fingerDraws.value)
    }
}
