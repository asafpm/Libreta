// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageImportTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun picture(
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
        transparent: Boolean = false,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (transparent) Color.TRANSPARENT else Color.rgb(30, 120, 200))
        // A mark in the top-left corner, to see where it goes.
        if (!transparent) for (y in 0 until MARK) for (x in 0 until width / 4) bitmap.setPixel(x, y, Color.RED)
        return ByteArrayOutputStream().also { bitmap.compress(format, 95, it) }.toByteArray()
    }

    private fun isJpeg(bytes: ByteArray) = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

    private fun isPng(bytes: ByteArray) = bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()

    @Test
    fun smallJpegsAndPngsAreKeptAsTheyAre() {
        val png = picture(300, 200, Bitmap.CompressFormat.PNG)
        val image = ImageImport.imported(png)
        assertArrayEquals(png, image.data.toByteArray())
        assertEquals(300 to 200, image.width to image.height)
    }

    @Test
    fun largePicturesAreMadeAtMostFourMegapixels() {
        val photo = ImageImport.imported(picture(3000, 2000, Bitmap.CompressFormat.PNG))
        assertTrue(photo.width.toLong() * photo.height <= ImageImport.MAX_PIXELS)
        assertEquals(1.5, photo.width.toDouble() / photo.height, 0.01)
        // Opaque: JPEG; with transparency: PNG.
        assertTrue(isJpeg(photo.data.toByteArray()))
        val drawing = ImageImport.imported(picture(3000, 2000, Bitmap.CompressFormat.PNG, transparent = true))
        assertTrue(isPng(drawing.data.toByteArray()))
    }

    @Test
    fun rotatedPhotosAreTurnedUpright() {
        val file = temporary.newFile("photo.jpg")
        file.writeBytes(picture(400, 200, Bitmap.CompressFormat.JPEG))
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val image = ImageImport.imported(file.readBytes())
        assertEquals(200 to 400, image.width to image.height)
        val bytes = image.data.toByteArray()
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(200 to 400, decoded.width to decoded.height)
        // The top-left mark is now along the top of the right edge.
        val mark = decoded.getPixel(200 - MARK / 2, 50)
        assertTrue(Integer.toHexString(mark), Color.red(mark) > 200 && Color.blue(mark) < 100)
    }

    @Test
    fun otherFormatsAreConverted() {
        @Suppress("DEPRECATION")
        val webp = picture(100, 80, Bitmap.CompressFormat.WEBP)
        val image = ImageImport.imported(webp)
        assertTrue(isJpeg(image.data.toByteArray()) || isPng(image.data.toByteArray()))
        assertEquals(100 to 80, image.width to image.height)
    }

    @Test
    fun whatIsNotAPictureFails() {
        assertFailsWith<IOException> { ImageImport.imported("not a picture".toByteArray()) }
    }

    private companion object {
        const val MARK = 10
    }
}
