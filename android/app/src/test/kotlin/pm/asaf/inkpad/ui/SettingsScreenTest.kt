// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Palette
import pm.asaf.inkpad.core.model.StylusButton
import pm.asaf.inkpad.library.SortOrder

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val chosen = ArrayList<Any>()

    private fun show(colors: ColorSettings = ColorSettings(Palette.DEFAULT_FIXED, 3)) {
        var shown by mutableStateOf(colors)
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    roots = emptyList(),
                    sortOrder = SortOrder.MODIFIED,
                    fingerDraws = false,
                    stylusButton = StylusButton.ERASER,
                    colors = shown,
                    actions =
                        SettingsActions(
                            {},
                            {},
                            { _, _ -> },
                            {},
                            {},
                            { chosen += it },
                            fixedCount = { chosen += "fixed $it" },
                            fixedColor = { index, color -> chosen += "fixed $index ${color.hex()}" },
                            recentLimit = {
                                chosen += "recent $it"
                                shown = ColorSettings(shown.fixed, it)
                            },
                        ),
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun choosesWhatTheStylusButtonDoes() {
        show()
        compose.onNodeWithText("Erases").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Highlights").performScrollTo().performClick()
        compose.onNodeWithText("Does nothing").performScrollTo().performClick()
        assertEquals(listOf(StylusButton.HIGHLIGHTER, StylusButton.NONE), chosen)
    }

    @Test
    fun setsTheFixedColours() {
        show()
        compose.onNodeWithContentDescription("More: Fixed colours").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Fewer: Fixed colours").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Change colour Red").performScrollTo().performClick()
        compose.onNodeWithTag("hex").performTextReplacement("#123456")
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf("fixed 6", "fixed 4", "fixed 2 #123456"), chosen)
    }

    @Test
    fun limitsTheRecentColours() {
        show(ColorSettings(listOf(Color.of(0, 0, 0)), 1))
        // At least one fixed colour.
        compose.onNodeWithContentDescription("Fewer: Fixed colours").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Fewer: Recent colours to keep").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Fewer: Recent colours to keep").performScrollTo().assertIsNotEnabled()
        assertEquals(listOf("recent 0"), chosen)
    }
}
