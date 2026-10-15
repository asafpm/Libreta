// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.DrawingType
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.TextSettings
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.ToolSettings

@RunWith(AndroidJUnit4::class)
class ToolPreferencesTest {
    private val preferences =
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun drawingTypesAndTextSettingsAreKept() {
        val tools = ToolPreferences(preferences)
        tools.change { it.copy(drawingType = DrawingType.ELLIPSE) }
        tools.selectTool(Tool.TEXT)
        tools.setColor(Color.of(200, 0, 0))
        tools.change { it.copy(text = it.text.copy(font = "Serif Bold", size = 18.0)) }
        val expected = TextSettings("Serif Bold", 18.0, Color.of(200, 0, 0))
        assertEquals(expected, tools.tools.value.text)
        // Text's colour is its own: the pen keeps its colour.
        assertEquals(ToolSettings().pen, tools.tools.value.pen)
        val again = ToolPreferences(preferences).tools.value
        assertEquals(DrawingType.ELLIPSE, again.drawingType)
        assertEquals(Tool.TEXT, again.tool)
        assertEquals(expected, again.text)
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
