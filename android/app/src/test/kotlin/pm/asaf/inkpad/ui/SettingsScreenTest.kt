// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.inkpad.core.model.StylusButton
import pm.asaf.inkpad.library.SortOrder

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun choosesWhatTheStylusButtonDoes() {
        val chosen = ArrayList<StylusButton>()
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    roots = emptyList(),
                    sortOrder = SortOrder.MODIFIED,
                    fingerDraws = false,
                    stylusButton = StylusButton.ERASER,
                    actions = SettingsActions({}, {}, { _, _ -> }, {}, {}, { chosen += it }),
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Erases").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Highlights").performScrollTo().performClick()
        compose.onNodeWithText("Does nothing").performScrollTo().performClick()
        assertEquals(listOf(StylusButton.HIGHLIGHTER, StylusButton.NONE), chosen)
    }
}
