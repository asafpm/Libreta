// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.test.core.app.ApplicationProvider
import pm.asaf.libreta.android.render.PdfPageRenderer
import pm.asaf.libreta.android.render.PdfPages
import java.io.File

/**
 * PDFs drawn as blank pages of their real sizes (read with PdfBox): Robolectric can't run
 * Android's PdfRenderer. [install] before a test, [uninstall] after.
 */
class BlankPdfPages(
    file: File,
) : PdfPageRenderer {
    private val sizes =
        file.inputStream().use {
            PdfPages.sizes(
                ApplicationProvider.getApplicationContext<Context>(),
                it,
            )
        }

    override val pageCount: Int = sizes.size

    override fun pageSize(index: Int): Pair<Double, Double> = sizes[index]

    override fun render(
        index: Int,
        bitmap: Bitmap,
        transform: Matrix,
    ) = bitmap.eraseColor(Color.WHITE)

    override fun close() = Unit

    companion object {
        private val android = PdfPageRenderer.open

        fun install() {
            PdfPageRenderer.open = ::BlankPdfPages
        }

        fun uninstall() {
            PdfPageRenderer.open = android
        }
    }
}
