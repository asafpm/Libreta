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
import pm.asaf.libreta.core.model.Palette

@RunWith(AndroidJUnit4::class)
class ColorPreferencesTest {
    private val preferences =
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    private val black = Color.of(0, 0, 0)
    private val royalBlue = Palette.DEFAULT_PEN
    private val red = Color.of(237, 83, 83)
    private val darkGreen = Color.of(58, 145, 4)
    private val brightYellow = Palette.HIGHLIGHTER

    private fun custom(n: Int) = Color.of(n, n, n + 1)

    @Test
    fun offersFiveFixedAndThreeRecentColoursByDefault() {
        val tools = ColorPreferences(preferences)
        assertEquals(listOf(black, royalBlue, red, darkGreen, brightYellow), tools.fixedColors.value)
        assertEquals(3, tools.recentLimit.value)
        for (n in 1..4) tools.addRecentColor(custom(n))
        assertEquals(listOf(custom(4), custom(3), custom(2)), tools.recentColors.value)
    }

    @Test
    fun moreFixedColoursComeFromThePalette() {
        val tools = ColorPreferences(preferences)
        tools.setFixedCount(7)
        // Xournal++'s palette in its order, without the colours already there.
        assertEquals(listOf(Palette.XOURNALPP[0], Palette.XOURNALPP[1]), tools.fixedColors.value.drop(5))
        tools.setFixedCount(2)
        assertEquals(listOf(black, royalBlue), tools.fixedColors.value)
        tools.setFixedCount(0)
        assertEquals(1, tools.fixedColors.value.size)
        tools.setFixedCount(100)
        assertEquals(ColorPreferences.MAX_FIXED, tools.fixedColors.value.size)
        assertEquals(
            ColorPreferences.MAX_FIXED,
            tools.fixedColors.value
                .distinct()
                .size,
        )
    }

    @Test
    fun fixedColoursAreNeverRecent() {
        val tools = ColorPreferences(preferences)
        tools.addRecentColor(red)
        tools.addRecentColor(custom(1))
        assertEquals(listOf(custom(1)), tools.recentColors.value)
        // Made fixed: no longer recent.
        tools.setFixedColor(0, custom(1))
        assertEquals(custom(1), tools.fixedColors.value[0])
        assertEquals(emptyList<Color>(), tools.recentColors.value)
    }

    @Test
    fun theRecentLimitTrimsAtOnce() {
        val tools = ColorPreferences(preferences)
        tools.setRecentLimit(5)
        for (n in 1..5) tools.addRecentColor(custom(n))
        tools.setRecentLimit(2)
        assertEquals(listOf(custom(5), custom(4)), tools.recentColors.value)
        tools.setRecentLimit(0)
        tools.addRecentColor(custom(9))
        assertEquals(emptyList<Color>(), tools.recentColors.value)
    }

    @Test
    fun rememberedAcrossInstances() {
        val tools = ColorPreferences(preferences)
        tools.setFixedCount(3)
        tools.setFixedColor(2, custom(7))
        tools.setRecentLimit(1)
        tools.addRecentColor(custom(1))
        val again = ColorPreferences(preferences)
        assertEquals(listOf(black, royalBlue, custom(7)), again.fixedColors.value)
        assertEquals(1, again.recentLimit.value)
        assertEquals(listOf(custom(1)), again.recentColors.value)
    }
}
