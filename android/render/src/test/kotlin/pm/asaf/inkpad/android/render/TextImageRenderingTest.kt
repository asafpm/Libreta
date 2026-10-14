// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Element
import kotlin.test.assertTrue

/** Renders src/test/resources/pages/texts.xopp and images.xopp (also viewable in Xournal++). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextImageRenderingTest {
    private val renderer = PageRenderer(ApplicationProvider.getApplicationContext())

    @Test
    fun texts() =
        Screenshots.assertMatches(renderer.renderToBitmap(TestPages.load("texts.xopp").pages[0], ZOOM), "texts/texts")

    @Test
    fun images() =
        Screenshots.assertMatches(
            renderer.renderToBitmap(TestPages.load("images.xopp").pages[0], ZOOM),
            "images/images",
        )

    @Test
    fun textBoundsGrowWithSizeAndLines() {
        fun size(
            text: String,
            points: Double,
        ) = renderer.textMeasurer.measure(Element.Text(text, "Sans", points, 0.0, 0.0, Color.BLACK))
        val small = size("Hello", 12.0)
        val large = size("Hello", 24.0)
        val twoLines = size("Hello\nHello", 12.0)
        assertTrue(large.width > 1.9 * small.width && large.height > 1.9 * small.height, "$small vs $large")
        assertTrue(twoLines.height > 1.9 * small.height && twoLines.width == small.width, "$small vs $twoLines")
        // DejaVu Sans at 12 pt: a line is about 14 pt high (ascent + descent).
        assertTrue(small.height in 13.0..15.0, "line height ${small.height}")
    }

    private companion object {
        const val ZOOM = 2f
    }
}
