// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.NewPageTemplate
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.Palette
import pm.asaf.libreta.core.model.PaperSize
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.StylusButton
import pm.asaf.libreta.library.LibraryRoot
import pm.asaf.libreta.library.SortOrder

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val chosen = ArrayList<Any>()

    private fun show(
        colors: ToolbarSettings = ToolbarSettings(Palette.DEFAULT_FIXED, 3),
        roots: List<LibraryRoot> = emptyList(),
    ) {
        var shown by mutableStateOf(colors)
        var template by mutableStateOf(NewPageTemplate())
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    roots = roots,
                    sortOrder = SortOrder.MODIFIED,
                    fingerButton = true,
                    stylusButton = StylusButton.ERASER,
                    toolbar = shown,
                    newPages = template,
                    actions =
                        SettingsActions(
                            {},
                            { chosen += "remove ${it.name}" },
                            { _, _ -> },
                            {},
                            { chosen += "finger button $it" },
                            { chosen += it },
                            fixedCount = { chosen += "fixed $it" },
                            fixedColor = { index, color -> chosen += "fixed $index ${color.hex()}" },
                            recentLimit = {
                                chosen += "recent $it"
                                shown = ToolbarSettings(shown.fixed, it, shown.sizes)
                            },
                            sizeVisible = { size, visible ->
                                chosen += "$size $visible"
                                shown =
                                    ToolbarSettings(
                                        shown.fixed,
                                        shown.recentLimit,
                                        if (visible) {
                                            shown.sizes + size
                                        } else {
                                            shown.sizes -
                                                size
                                        },
                                    )
                            },
                            newPages = {
                                chosen += it
                                template = it
                            },
                        ),
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun removingALibraryFolderAsksFirst() {
        show(roots = listOf(LibraryRoot.Local(android.net.Uri.parse("content://notes/tree/root"), "Notes")))
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.onNodeWithText("Remove Notes from the library?").assertIsDisplayed()
        assertEquals(emptyList<Any>(), chosen)
        compose.onNodeWithText("Remove").performClick()
        assertEquals(listOf<Any>("remove Notes"), chosen)
    }

    @Test
    fun aboutShowsTheLicencesOfWhatTheAppIncludes() {
        show()
        compose.onNodeWithText("Source code").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Licences").performScrollTo().performClick()
        compose.onNodeWithText("PdfBox-Android (Apache PDFBox)").performScrollTo().performClick()
        compose.onNodeWithText("Adobe Font Metrics", substring = true).assertExists()
        compose.onAllNodesWithContentDescription("Back")[1].performClick()
        compose.onNodeWithText("Libreta").performClick()
        compose.onNodeWithText("Version 3, 29 June 2007", substring = true).assertExists()
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
    fun choosesTheWidthsInTheToolbar() {
        show(ToolbarSettings(Palette.DEFAULT_FIXED, 3, setOf(PenSize.FINE, PenSize.THICK)))
        compose
            .onNodeWithContentDescription("Medium")
            .performScrollTo()
            .assertIsOff()
            .performClick()
        compose
            .onNodeWithContentDescription("Fine")
            .performScrollTo()
            .assertIsOn()
            .performClick()
        compose.onNodeWithContentDescription("Medium").performScrollTo().performClick()
        // The last one shown can't be hidden.
        compose
            .onNodeWithContentDescription("Thick")
            .performScrollTo()
            .assertIsOn()
            .assertIsNotEnabled()
        assertEquals(listOf("MEDIUM true", "FINE false", "MEDIUM false"), chosen)
    }

    @Test
    fun choosesHowNewPagesLook() {
        show()
        compose
            .onNodeWithText("Copy the page before")
            .performScrollTo()
            .assertIsOn()
            .performClick()
        compose.onNodeWithText("Graph").performScrollTo().performClick()
        compose.onNodeWithText("Letter").performScrollTo().performClick()
        compose.onNodeWithText("Landscape").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Yellow").performScrollTo().performClick()
        assertEquals(
            NewPageTemplate(false, PaperSize.LETTER, landscape = true, PageStyle.GRAPH, Color.of(0xff, 0xff, 0x80)),
            chosen.last(),
        )
    }

    @Test
    fun choosesWhetherTheToolbarHasTheFingerButton() {
        show()
        compose
            .onNodeWithText("“Draw with finger” in the toolbar")
            .performScrollTo()
            .assertIsOn()
            .performClick()
        assertEquals(listOf("finger button false"), chosen)
    }

    @Test
    fun limitsTheRecentColours() {
        show(ToolbarSettings(listOf(Color.of(0, 0, 0)), 1))
        // At least one fixed colour.
        compose.onNodeWithContentDescription("Fewer: Fixed colours").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Fewer: Recent colours to keep").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Fewer: Recent colours to keep").performScrollTo().assertIsNotEnabled()
        assertEquals(listOf("recent 0"), chosen)
    }
}
