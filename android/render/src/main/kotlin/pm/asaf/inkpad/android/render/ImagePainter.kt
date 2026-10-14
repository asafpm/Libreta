// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8 src/core/view/ImageView.cpp, TexImageView.cpp and
// src/core/model/Image.cpp, TexImage.cpp, © Xournal++ contributors.

package pm.asaf.inkpad.android.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import okio.ByteString
import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Element
import kotlin.math.ceil

/** Draws images and LaTeX formulas (docs/rendering.md §3.5), stretched into their box. */
internal class ImagePainter(
    private val pdfRasterizer: PdfRasterizer,
) {
    private val paint = Paint().apply { isFilterBitmap = true }
    private val decoded = LruCache<ByteString, Bitmap>(DECODED_CACHE_SIZE)

    fun paint(
        canvas: Canvas,
        image: Element.Image,
    ) {
        decode(image.data)?.let { draw(canvas, it, image.bounds) }
    }

    fun paint(
        canvas: Canvas,
        tex: Element.TexImage,
        zoom: Float,
    ) {
        val bitmap =
            if (tex.data.isPdf()) {
                val width = ceil((tex.bounds.right - tex.bounds.left) * zoom).toInt().coerceAtLeast(1)
                val height = ceil((tex.bounds.bottom - tex.bounds.top) * zoom).toInt().coerceAtLeast(1)
                pdfRasterizer.render(tex.data, 0, width, height)
            } else {
                decode(tex.data)
            }
        bitmap?.let { draw(canvas, it, tex.bounds) }
    }

    private fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        bounds: Bounds,
    ) {
        canvas.drawBitmap(bitmap, null, bounds.toRectF(), paint)
    }

    /** Decodes PNG, JPEG, …, applying the EXIF orientation like Xournal++; null if undecodable. */
    private fun decode(data: ByteString): Bitmap? =
        decoded.get(data) ?: decodeUncached(data)?.also { decoded.put(data, it) }

    private fun decodeUncached(data: ByteString): Bitmap? {
        val bytes = data.toByteArray()
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
        if (size.outWidth <= 0 || size.outHeight <= 0) return null
        // Xournal++ shrinks images above 2^25 pixels.
        var sample = 1
        while (size.outWidth.toLong() / sample * (size.outHeight / sample) > MAX_PIXELS) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { orient(it, bytes) }
    }

    private fun orient(
        bitmap: Bitmap,
        bytes: ByteArray,
    ): Bitmap {
        val orientation =
            runCatching {
                ExifInterface(
                    bytes.inputStream(),
                ).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(HALF_TURN)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { setRotate(QUARTER_TURN) }.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(QUARTER_TURN)
            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { setRotate(-QUARTER_TURN) }.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-QUARTER_TURN)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** LaTeX data is PDF when bytes 1–3 are "PDF" (TexImage.cpp), otherwise an image. */
    private fun ByteString.isPdf(): Boolean = size > PDF_MAGIC_END && substring(1, PDF_MAGIC_END + 1).utf8() == "PDF"

    private companion object {
        const val DECODED_CACHE_SIZE = 16
        const val MAX_PIXELS = 1L shl 25
        const val QUARTER_TURN = 90f
        const val HALF_TURN = 180f
        const val PDF_MAGIC_END = 3
    }
}
