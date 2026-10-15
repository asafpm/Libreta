// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.files.PageThumbnails
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w900dp-h1400dp")
class PagePanelTest {
    @get:Rule
    val compose = createComposeRule()

    private val moves = ArrayList<Pair<Int, Int>>()

    private fun show(count: Int = 4) {
        val physics =
            File(checkNotNull(System.getProperty("inkpad.testdata")), "physics.xopp")
                .source()
                .buffer()
                .use { XoppReader.read(it) }
                .document
        compose.setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                val thumbnails =
                    PageThumbnails(
                        scope,
                        Dispatchers.Default,
                        newRenderer = { PageRenderer(ApplicationProvider.getApplicationContext()) },
                    )
                PagePanel(
                    physics.pages.take(count),
                    current = 0,
                    thumbnails,
                    PageActions({}, {}, {}, {}, { from, to -> moves += from to to }, {}),
                )
            }
        }
    }

    @Test
    fun commandsAreInOneRowAndAHintTellsHowToMovePages() {
        show()
        val top = compose.onNodeWithContentDescription("Insert a page after this one").getBoundsInRoot().top
        for (command in listOf("Duplicate page", "Delete page", "Page background")) {
            assertEquals(command, top, compose.onNodeWithContentDescription(command).getBoundsInRoot().top)
        }
        // Pages are moved by dragging them, as the hint says, not by buttons.
        compose.onNodeWithText(HINT).assertIsDisplayed()
        compose.onNodeWithContentDescription("Move page up").assertDoesNotExist()
    }

    @Test
    // Dense, like a tablet: smaller than the frame in pixels, the bitmap is scaled up to it.
    @Config(qualifiers = "w900dp-h1400dp-xxhdpi")
    fun thumbnailsFillTheirFrame() {
        show(count = 1)
        // Inside the page's merged node.
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("thumbnail", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val image = compose.onNodeWithTag("thumbnail", useUnmergedTree = true).getBoundsInRoot()
        // As wide as thumbnails are shown, at the page's proportions (1 dp for rounding).
        assertEquals(THUMBNAIL_DP.toFloat(), (image.right - image.left).value, 1f)
        assertEquals(THUMBNAIL_DP * PAGE_HEIGHT / PAGE_WIDTH, (image.bottom - image.top).value, 1f)
    }

    @Test
    fun aSinglePageHasNoHint() {
        show(count = 1)
        compose.onNodeWithText(HINT).assertDoesNotExist()
    }

    @Test
    fun pagesAreDraggedToTheirPlace() {
        show()
        val first = compose.onNodeWithContentDescription("Page 1").getBoundsInRoot()
        val third = compose.onNodeWithContentDescription("Page 3").getBoundsInRoot()
        // From the middle of page 1 to the middle of page 3.
        val distance =
            with(compose.density) { ((third.top + third.bottom) / 2 - (first.top + first.bottom) / 2).toPx() }
        compose.onNodeWithContentDescription("Page 1").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(STEPS) { moveBy(Offset(0f, distance / STEPS)) }
            up()
        }
        assertEquals(listOf(0 to 2), moves)
        // A short hold and a drag is a scroll, not a move.
        compose.onNodeWithContentDescription("Page 2").performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, -distance / STEPS)) }
            up()
        }
        assertEquals(listOf(0 to 2), moves)
    }

    @Test
    fun draggingToTheEndScrollsTheList() {
        show(count = 8)
        val list = compose.onNodeWithTag("pages").getBoundsInRoot()
        val first = compose.onNodeWithContentDescription("Page 1").getBoundsInRoot()
        val toBottom = with(compose.density) { (list.bottom - 10.dp - (first.top + first.bottom) / 2).toPx() }
        compose.onNodeWithContentDescription("Page 1").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(STEPS) { moveBy(Offset(0f, toBottom / STEPS)) }
            // Held at the bottom while the list scrolls.
            repeat(STEPS * 4) {
                advanceEventTime(50)
                moveBy(Offset(0f, 0.1f))
            }
            up()
        }
        val (from, to) = moves.single()
        assertEquals(0, from)
        assertTrue("dropped at $to", to >= 5)
    }

    private companion object {
        const val HINT = "Hold a page and drag it to move it"
        const val TIMEOUT = 10_000L

        /** physics.xopp's first page, Letter. */
        const val PAGE_WIDTH = 611.99998f
        const val PAGE_HEIGHT = 791.99998f
        const val STEPS = 20
    }
}
