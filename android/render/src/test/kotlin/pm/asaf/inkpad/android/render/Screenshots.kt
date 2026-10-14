// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Compares rendered bitmaps with reference PNGs in src/test/screenshots. Record references
 * with `-Pinkpad.recordScreenshots=true`; on a mismatch the actual image and a diff are
 * written to build/screenshot-diffs.
 */
object Screenshots {
    private val referenceDir = File(checkNotNull(System.getProperty("inkpad.screenshots")))
    private val diffDir = File(checkNotNull(System.getProperty("inkpad.screenshotDiffs")))
    private val record = System.getProperty("inkpad.recordScreenshots").toBoolean()

    /** A pixel differs when any channel differs by more than this. */
    private const val CHANNEL_TOLERANCE = 2

    /** The image differs when more than this fraction of its pixels differ. */
    private const val MAX_DIFFERING_FRACTION = 0.001

    fun assertMatches(
        actual: Bitmap,
        name: String,
    ) {
        val reference = File(referenceDir, "$name.png")
        if (record) {
            reference.parentFile?.mkdirs()
            reference.outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        if (!reference.exists()) {
            save(actual, "$name.actual.png")
            fail("No reference screenshot $reference; record it with -Pinkpad.recordScreenshots=true")
        }
        val expected = checkNotNull(BitmapFactory.decodeFile(reference.path)) { "cannot decode $reference" }
        if (expected.width != actual.width || expected.height != actual.height) {
            save(actual, "$name.actual.png")
            fail("$name: size ${actual.width}x${actual.height}, reference ${expected.width}x${expected.height}")
        }
        val differing = countDifferingPixels(expected, actual)
        val fraction = differing.toDouble() / (actual.width * actual.height)
        if (fraction > MAX_DIFFERING_FRACTION) {
            save(actual, "$name.actual.png")
            save(diffImage(expected, actual), "$name.diff.png")
        }
        assertTrue(fraction <= MAX_DIFFERING_FRACTION, "$name: $differing pixels differ (see $diffDir)")
    }

    private fun countDifferingPixels(
        expected: Bitmap,
        actual: Bitmap,
    ): Int {
        val a =
            IntArray(actual.width * actual.height).also {
                actual.getPixels(it, 0, actual.width, 0, 0, actual.width, actual.height)
            }
        val e =
            IntArray(
                a.size,
            ).also { expected.getPixels(it, 0, expected.width, 0, 0, expected.width, expected.height) }
        return a.indices.count { !similar(a[it], e[it]) }
    }

    private fun similar(
        first: Int,
        second: Int,
    ): Boolean =
        (0 until Int.SIZE_BITS step Byte.SIZE_BITS).all { shift ->
            abs((first ushr shift and 0xff) - (second ushr shift and 0xff)) <=
                CHANNEL_TOLERANCE
        }

    /** Differing pixels in red over a faded copy of the reference. */
    private fun diffImage(
        expected: Bitmap,
        actual: Bitmap,
    ): Bitmap {
        val diff = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                val reference = expected.getPixel(x, y)
                val faded = 0xff000000.toInt() or (0xc0c0c0 + (reference and 0x3f3f3f))
                diff.setPixel(x, y, if (similar(reference, actual.getPixel(x, y))) faded else 0xffff0000.toInt())
            }
        }
        return diff
    }

    private fun save(
        bitmap: Bitmap,
        fileName: String,
    ) {
        val file = File(diffDir, fileName)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
