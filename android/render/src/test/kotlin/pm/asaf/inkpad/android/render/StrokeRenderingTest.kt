// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.source
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.core.model.Document

/** Renders the pages of src/test/resources/pages/strokes.xopp (also viewable in Xournal++). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StrokeRenderingTest {
    private val renderer = PageRenderer(ApplicationProvider.getApplicationContext())
    private val document: Document = TestPages.load("strokes.xopp")

    private fun assertPage(
        index: Int,
        name: String,
    ) = Screenshots.assertMatches(renderer.renderToBitmap(document.pages[index], ZOOM), "strokes/$name")

    @Test
    fun capsJoinsAndDashes() = assertPage(0, "caps-joins-dashes")

    @Test
    fun pressure() = assertPage(1, "pressure")

    @Test
    fun highlighters() = assertPage(2, "highlighters")

    @Test
    fun fillsAndEraser() = assertPage(3, "fills-eraser")

    @Test
    fun pressureRepair() = assertPage(4, "pressure-repair")

    private companion object {
        const val ZOOM = 2f
    }
}

/** Test documents in src/test/resources/pages. */
object TestPages {
    fun load(name: String): Document {
        val stream =
            checkNotNull(
                TestPages::class.java.classLoader?.getResourceAsStream("pages/$name"),
            ) { "missing pages/$name" }
        return XoppReader.read(stream.source()).document
    }
}
