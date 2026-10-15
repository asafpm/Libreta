// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.math.sqrt

/** A picture to insert: its encoded bytes and size in pixels, upright. */
class ImportedImage(
    val data: ByteString,
    val width: Int,
    val height: Int,
)

/**
 * Pictures to insert into notes, from the photo picker, the camera or another app. They are
 * kept as they are (JPEG or PNG) when that works everywhere; otherwise they are turned upright
 * (Xournal++ ignores EXIF orientation), made at most [MAX_PIXELS], and stored as JPEG, or PNG
 * when they have transparency.
 */
object ImageImport {
    /** Four megapixels: sharp on a page, small in a note. */
    const val MAX_PIXELS = 4_000_000

    /** Reads the picture at [uri]; fails with an [IOException] if it is not one. */
    @Throws(IOException::class)
    fun read(
        context: Context,
        uri: Uri,
    ): ImportedImage {
        val bytes =
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw FileNotFoundException(uri.toString())
        return imported(bytes)
    }

    @Throws(IOException::class)
    fun imported(bytes: ByteArray): ImportedImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a picture")
        val exif = runCatching { ExifInterface(bytes.inputStream()) }.getOrNull()
        val degrees = exif?.rotationDegrees ?: 0
        val flipped = exif?.isFlipped == true
        val plain = degrees == 0 && !flipped
        val small = bounds.outWidth.toLong() * bounds.outHeight <= MAX_PIXELS
        if (plain && small && bounds.outMimeType in KEPT_TYPES) {
            return ImportedImage(bytes.toByteString(), bounds.outWidth, bounds.outHeight)
        }
        val decoded = decode(bytes, bounds.outWidth, bounds.outHeight) ?: throw IOException("Cannot decode the picture")
        val upright = upright(decoded, degrees, flipped)
        val output = ByteArrayOutputStream()
        val format = if (transparent(upright)) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        upright.compress(format, JPEG_QUALITY, output)
        return ImportedImage(output.toByteArray().toByteString(), upright.width, upright.height)
    }

    /** Whether any pixel of [bitmap] lets the page show through (an alpha channel alone isn't enough). */
    private fun transparent(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val row = IntArray(bitmap.width)
        return (0 until bitmap.height).any { y ->
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            row.any { it ushr ALPHA_SHIFT != OPAQUE }
        }
    }

    /** [bytes] decoded at most [MAX_PIXELS]: subsampled, then scaled down to fit. */
    private fun decode(
        bytes: ByteArray,
        width: Int,
        height: Int,
    ): Bitmap? {
        var sample = 1
        while (width.toLong() / (sample * 2) * (height / (sample * 2)) >= MAX_PIXELS) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { decoded ->
            val scale = sqrt(MAX_PIXELS.toDouble() / (decoded.width.toLong() * decoded.height))
            if (scale >= 1) {
                decoded
            } else {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt(),
                    (decoded.height * scale).toInt(),
                    true,
                )
            }
        }
    }

    private fun upright(
        bitmap: Bitmap,
        degrees: Int,
        flipped: Boolean,
    ): Bitmap {
        if (degrees == 0 && !flipped) return bitmap
        val matrix =
            Matrix().apply {
                if (flipped) postScale(-1f, 1f)
                postRotate(degrees.toFloat())
            }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Formats Xournal++ reads everywhere, kept as they are. */
    private val KEPT_TYPES = setOf("image/jpeg", "image/png")
    private const val JPEG_QUALITY = 90
    private const val ALPHA_SHIFT = 24
    private const val OPAQUE = 0xFF
}
