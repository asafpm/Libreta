// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color.rgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.FileDomain
import pm.asaf.inkpad.core.model.Layer
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.model.PageStyle
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundRenderingTest {
    private val renderer = PageRenderer(ApplicationProvider.getApplicationContext())

    /** Half of an A4 page, at 1.5 px per point, so lines stay visible. */
    private fun page(background: Background?) =
        Page(width = 298.0, height = 421.0, background = background, layers = listOf(Layer(emptyList())))

    private fun solid(
        style: PageStyle,
        config: String? = null,
        color: Color = Color.WHITE,
    ) = page(Background.Solid(color, style, config))

    private fun assertRenders(
        page: Page,
        name: String,
        source: BackgroundSource = BackgroundSource.None,
    ) {
        val bitmap = PageRenderer(ApplicationProvider.getApplicationContext(), source).renderToBitmap(page, ZOOM)
        Screenshots.assertMatches(bitmap, "backgrounds/$name")
    }

    @Test
    fun plain() = assertRenders(solid(PageStyle.PLAIN, color = Color(0xffc0d4ffu)), "plain-pink")

    @Test
    fun ruled() = assertRenders(solid(PageStyle.RULED), "ruled")

    @Test
    fun lined() = assertRenders(solid(PageStyle.LINED), "lined")

    @Test
    fun linedDark() = assertRenders(solid(PageStyle.LINED, color = Color(0x303030ffu)), "lined-dark")

    @Test
    fun staves() = assertRenders(solid(PageStyle.STAVES), "staves")

    @Test
    fun graph() = assertRenders(solid(PageStyle.GRAPH), "graph")

    @Test
    fun graphWithBorderAndBoldLines() =
        assertRenders(solid(PageStyle.GRAPH, "m1=20,rm=1,bli=5,blw=1"), "graph-border-bold")

    @Test
    fun dotted() = assertRenders(solid(PageStyle.DOTTED), "dotted")

    @Test
    fun isoDotted() = assertRenders(solid(PageStyle.ISO_DOTTED), "isodotted")

    @Test
    fun isoGraph() = assertRenders(solid(PageStyle.ISO_GRAPH), "isograph")

    @Test
    fun pixmapIsStretchedToThePage() {
        // A 2x1 image: left half red, right half blue.
        val image = Bitmap.createBitmap(intArrayOf(rgb(255, 0, 0), rgb(0, 0, 255)), 2, 1, Bitmap.Config.ARGB_8888)
        val source =
            object : BackgroundSource by BackgroundSource.None {
                override fun image(background: Background.Pixmap) = image
            }
        assertRenders(page(Background.Pixmap(FileDomain.ABSOLUTE, "x.png")), "pixmap", source)
    }

    @Test
    fun missingPdf() {
        // A full A4 page: the placeholder text is wider than the half pages used elsewhere.
        val a4 =
            Page(
                width = 595.28,
                height = 841.89,
                background = Background.Pdf(pageNumber = 1),
                layers = listOf(Layer(emptyList())),
            )
        Screenshots.assertMatches(renderer.renderToBitmap(a4, 0.75f), "backgrounds/pdf-missing")
    }

    @Test
    fun pdfPageIsDrawnBySource() {
        val source =
            object : BackgroundSource by BackgroundSource.None {
                override fun drawPdfPage(
                    canvas: Canvas,
                    pageNumber: Long,
                    zoom: Float,
                ): Boolean {
                    canvas.drawColor(rgb(0, 128, 0))
                    return pageNumber == 3L
                }
            }
        val bitmap =
            PageRenderer(
                ApplicationProvider.getApplicationContext(),
                source,
            ).renderToBitmap(page(Background.Pdf(3)), ZOOM)
        assertEquals(rgb(0, 128, 0), bitmap.getPixel(10, 10))
    }

    @Test
    fun clipLimitsDrawing() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        renderer.render(
            solid(PageStyle.PLAIN, color = Color.BLACK),
            Canvas(bitmap),
            1f,
            pm.asaf.inkpad.core.model
                .Bounds(0.0, 0.0, 50.0, 50.0),
        )
        assertEquals(rgb(0, 0, 0), bitmap.getPixel(25, 25))
        assertEquals(0, bitmap.getPixel(75, 75)) // untouched (transparent)
    }

    private companion object {
        const val ZOOM = 1.5f
    }
}
