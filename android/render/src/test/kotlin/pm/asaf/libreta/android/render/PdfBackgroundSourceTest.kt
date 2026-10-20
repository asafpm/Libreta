// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfBackgroundSourceTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val android = PdfPageRenderer.open

    /** Robolectric can't run PdfRenderer: a page of A4 that fails once closed, as PdfRenderer does. */
    private class Pages : PdfPageRenderer {
        var closed = false

        override val pageCount = 1

        override fun pageSize(index: Int) = 595.0 to 842.0

        override fun render(
            index: Int,
            bitmap: Bitmap,
            transform: Matrix,
        ) {
            check(!closed) { "Already closed" }
            bitmap.eraseColor(android.graphics.Color.WHITE)
        }

        override fun close() {
            closed = true
        }
    }

    private lateinit var pages: Pages

    @Before
    fun setUp() {
        pages = Pages()
        PdfPageRenderer.open = { pages }
    }

    @After
    fun tearDown() {
        PdfPageRenderer.open = android
    }

    private fun canvas() = Canvas(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))

    @Test
    fun aClosedPdfDrawsNothing() {
        val source = PdfBackgroundSource(temporary.newFile("paper.pdf"))
        assertTrue(source.drawPdfPage(canvas(), 1, 1f))
        source.close()
        // A render still running when its note closed: nothing, rather than an exception.
        assertFalse(source.drawPdfPage(canvas(), 1, 2f))
    }
}
