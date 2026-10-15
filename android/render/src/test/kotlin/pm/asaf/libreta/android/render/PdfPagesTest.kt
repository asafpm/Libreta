// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class PdfPagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A PDF of [pages], protected by [protect] if given. */
    private fun pdf(
        vararg pages: PDPage,
        protect: StandardProtectionPolicy? = null,
    ): ByteArray {
        PdfPages.init(context)
        return PDDocument().use { document ->
            pages.forEach(document::addPage)
            protect?.let(document::protect)
            ByteArrayOutputStream().also(document::save).toByteArray()
        }
    }

    private fun sizes(bytes: ByteArray) = PdfPages.sizes(context, bytes.inputStream())

    @Test
    fun pagesHaveTheirSizeInPoints() {
        val bytes = pdf(PDPage(PDRectangle(0f, 0f, 612f, 792f)), PDPage(PDRectangle(0f, 0f, 595.28f, 841.89f)))
        val sizes = sizes(bytes)
        assertEquals(2, sizes.size)
        assertEquals(612.0, sizes[0].first, 0.001)
        assertEquals(792.0, sizes[0].second, 0.001)
        assertEquals(595.28, sizes[1].first, 0.001)
        assertEquals(841.89, sizes[1].second, 0.001)
    }

    @Test
    fun cropBoxesAndRotationAreShownAsReadersShowThem() {
        val cropped = PDPage(PDRectangle(0f, 0f, 612f, 792f)).apply { cropBox = PDRectangle(50f, 100f, 400f, 500f) }
        val turned = PDPage(PDRectangle(0f, 0f, 612f, 792f)).apply { rotation = 90 }
        val back = PDPage(PDRectangle(0f, 0f, 612f, 792f)).apply { rotation = -270 }
        val sizes = sizes(pdf(cropped, turned, back))
        assertEquals(listOf(400.0 to 500.0, 792.0 to 612.0, 792.0 to 612.0), sizes)
    }

    @Test
    fun pdfsProtectedOnlyForEditingOpen() {
        // Publishers' PDFs often restrict editing, with no password needed to read.
        val policy = StandardProtectionPolicy("owner", "", AccessPermission()).apply { encryptionKeyLength = 128 }
        assertEquals(listOf(612.0 to 792.0), sizes(pdf(PDPage(PDRectangle(0f, 0f, 612f, 792f)), protect = policy)))
    }

    @Test
    fun aPdfNeedingAPasswordOrNotAPdfFails() {
        val policy = StandardProtectionPolicy("owner", "secret", AccessPermission()).apply { encryptionKeyLength = 128 }
        assertFailsWith<IOException> { sizes(pdf(PDPage(), protect = policy)) }
        assertFailsWith<IOException> { sizes("not a pdf".toByteArray()) }
    }

    @Test
    fun theSamplePdfIsRead() {
        val sample = File(checkNotNull(System.getProperty("libreta.testdata")), "pdf_annotation.xopp.bg.pdf")
        val sizes = PdfPages.sizes(context, sample.inputStream())
        assertEquals(792.0 to 612.0, sizes.first().let { Math.round(it.first) * 1.0 to Math.round(it.second) * 1.0 })
    }
}
