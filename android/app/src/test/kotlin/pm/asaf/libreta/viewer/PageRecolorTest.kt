// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageRecolorTest {
    @Test
    fun paperBecomesDarkAndBlackInkLight() {
        assertEquals(0xFF303446.toInt(), PageRecolor.map(Color.WHITE))
        assertEquals(0xFFC6D0F5.toInt(), PageRecolor.map(Color.BLACK))
        // Other colours are inverted between them; alpha is kept.
        val red = PageRecolor.map(Color.argb(128, 255, 0, 0))
        assertEquals(128, Color.alpha(red))
        assertEquals(0x30, Color.red(red))
        assertEquals(0xD0, Color.green(red))
        assertEquals(0xF5, Color.blue(red))
    }

    @Test
    fun theFilterDrawsWhatMapGives() {
        val bitmap = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        val source = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        val colors = intArrayOf(Color.WHITE, Color.BLACK, Color.rgb(237, 83, 83))
        source.setPixels(colors, 0, 3, 0, 0, 3, 1)
        Canvas(bitmap).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = PageRecolor.filter() })
        for (i in colors.indices) {
            val drawn = bitmap.getPixel(i, 0)
            val expected = PageRecolor.map(colors[i])
            for (shift in listOf(0, 8, 16)) {
                assertEquals(1.0, abs((drawn ushr shift and 255) - (expected ushr shift and 255)).toDouble(), 1.0)
            }
        }
    }
}
