// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.inkpad.core.model.Background
import java.io.File

/**
 * Renders every page of the gzip/plain sample documents for scripts/render-crosscheck.sh.
 * Does nothing unless the `inkpad.renderExportDir` system property is set.
 *
 * Writes `<dir>/<document>/page-<n>.png` and `<dir>/manifest.tsv` (document, page count,
 * pages with PDF backgrounds, which cannot be rendered on the JVM).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderExportTest {
    @Test
    fun exportSamplePages() {
        val outDir = System.getProperty("inkpad.renderExportDir")?.let(::File) ?: return
        outDir.deleteRecursively()
        val manifest = StringBuilder()
        for (path in DOCUMENTS) {
            val file = TestDocuments.file(path)
            val document = TestDocuments.load(path)
            val renderer = PageRenderer(ApplicationProvider.getApplicationContext(), SidecarImages(file))
            val pdfPages = mutableListOf<Int>()
            document.pages.forEachIndexed { index, page ->
                if (page.background is Background.Pdf) pdfPages += index + 1
                val target = File(outDir, "$path/page-${index + 1}.png").apply { parentFile?.mkdirs() }
                target.outputStream().use {
                    renderer
                        .renderToBitmap(
                            page,
                            ZOOM,
                        ).compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            manifest.append("$path\t${document.pages.size}\t${pdfPages.joinToString(",")}\n")
        }
        File(outDir, "manifest.tsv").writeText(manifest.toString())
    }

    private companion object {
        /** 108 dpi. */
        const val ZOOM = 1.5f

        val DOCUMENTS =
            listOf(
                "note-taking.xopp",
                "pdf_annotation.xopp",
                "physics.xopp",
                "inkpad-edits.xopp",
                "muzzy_pixmap_bg.xopp",
                "2026-10-13-Note-22-46.xoj",
                "xournalpp/cjk/测试.xopp",
                "xournalpp/development/README-Eraser-and-padded-box.xopp",
                "xournalpp/development/Setsquare_annotations.xopp",
                "xournalpp/load/layer.xoj",
                "xournalpp/load/linebreaksLatex.xopp",
                "xournalpp/load/pages.xoj",
                "xournalpp/load/relativePaths.xopp",
                "xournalpp/load/text.xml",
                "xournalpp/packaged_xopp/imgAttachment/doc_with_jpg.xopp",
                "xournalpp/packaged_xopp/imgAttachment/old.xopp",
                "xournalpp/packaged_xopp/imgBackground/old.xopp",
                "xournalpp/packaged_xopp/stroke/old.xopp",
                "xournalpp/packaged_xopp/suite.xopp",
                "xournalpp/pageTypeFormatCopy.xopp",
                "xournalpp/preview-test2.xoj",
                "xournalpp/test1.xoj",
            )
    }
}
