// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.buffer
import okio.source
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.Document
import pm.asaf.inkpad.core.model.FileDomain
import java.io.File

/** Pages of the sample documents in testdata/ (PDF content needs a device, see androidTest). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentRenderingTest {
    private fun assertPage(
        path: String,
        pageIndex: Int,
        name: String,
        zoom: Float = 1f,
    ) {
        val file = TestDocuments.file(path)
        val document = TestDocuments.load(path)
        val renderer = PageRenderer(ApplicationProvider.getApplicationContext(), SidecarImages(file))
        Screenshots.assertMatches(renderer.renderToBitmap(document.pages[pageIndex], zoom), "documents/$name")
    }

    @Test
    fun physics() = assertPage("physics.xopp", 0, "physics-p1")

    @Test
    fun noteTaking() = assertPage("note-taking.xopp", 0, "note-taking-p1")

    @Test
    fun pixmapBackgroundMissing() = assertPage("muzzy_pixmap_bg.xopp", 0, "muzzy-p1", zoom = 2f)

    @Test
    fun allRulingsAndAttachedImage() {
        for (index in 0 until 6) assertPage("xournalpp/load/pages.xoj", index, "pages-xoj-p${index + 1}", zoom = 0.5f)
    }

    @Test
    fun suite() = assertPage("xournalpp/packaged_xopp/suite.xopp", 0, "suite-p1")
}

/** testdata/ files. */
object TestDocuments {
    val root = File(checkNotNull(System.getProperty("inkpad.testdata")))

    fun file(path: String): File = File(root, path)

    fun load(path: String): Document =
        file(path).source().buffer().use { XoppReader.read(it.readByteString()).document }
}

/**
 * Image backgrounds next to a gzip document: `attach` files are `<document>.<filename>`,
 * `absolute` ones are relative to the document's directory (docs/xopp-format.md §4).
 * Only files inside testdata/ are used, so results do not depend on the machine.
 */
class SidecarImages(
    private val document: File,
) : BackgroundSource by BackgroundSource.None {
    override fun image(background: Background.Pixmap): Bitmap? =
        locate(background)
            ?.canonicalFile
            ?.takeIf { it.isFile && it.startsWith(TestDocuments.root.canonicalFile) }
            ?.let { BitmapFactory.decodeFile(it.path) }

    private fun locate(background: Background.Pixmap): File? {
        val name = background.filename ?: return null
        return when (background.domain) {
            FileDomain.ATTACH -> File(document.parentFile, "${document.name}.$name")
            FileDomain.ABSOLUTE -> File(name).takeIf { it.isAbsolute } ?: File(document.parentFile, name)
            FileDomain.CLONE, null -> null
        }
    }
}
