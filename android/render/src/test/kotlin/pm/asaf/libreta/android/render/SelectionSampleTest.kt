// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.buffer
import okio.sink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.format.XoppWriter
import pm.asaf.libreta.core.geometry.Selecting
import pm.asaf.libreta.core.geometry.SelectionBox
import pm.asaf.libreta.core.geometry.SelectionHandle
import pm.asaf.libreta.core.geometry.SelectionShape
import pm.asaf.libreta.core.geometry.SelectionTransform
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.NewPageTemplate
import pm.asaf.libreta.core.model.PageOperations
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.SelectionOperations
import pm.asaf.libreta.core.model.applying
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * testdata/libreta-selection.xopp: libreta-notes.xopp edited with Libreta's selection (strokes
 * rotated, scaled, mirrored and restyled; text scaled and recoloured; an image stretched and a
 * TeX image scaled; strokes moved to a new page, with copies left), for scripts/render-crosscheck.sh
 * (Xournal++ must open it and show the same). Written again when screenshots are recorded;
 * otherwise it must match.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectionSampleTest {
    private val measurer = PageRenderer(ApplicationProvider.getApplicationContext()).textMeasurer
    private var document = TestDocuments.load("libreta-notes.xopp").copy(creator = "Libreta selection sample")

    @Test
    fun theSampleIsWhatLibretaSelectionMakes() {
        edit()
        // As saved: the file keeps eight digits.
        val saved = XoppReader.read(XoppWriter.toByteString(document)).document
        val file = TestDocuments.file(PATH)
        if (System.getProperty("libreta.recordScreenshots").toBoolean()) {
            file.sink().buffer().use { XoppWriter.write(saved, it) }
        }
        assertEquals(saved, TestDocuments.load(PATH))
    }

    private fun edit() {
        // The start of the first two lines (strokes only): a twelfth of a turn up.
        drag(INK, Region(40.0, 80.0, 300.0, 135.0), SelectionHandle.ROTATE) { box ->
            box.centerX + ROTATE_REACH * cos(-PI / 6) to box.centerY + ROTATE_REACH * sin(-PI / 6)
        }
        // The end of three lines, mirrored by their left edge taken past the right one.
        drag(INK, Region(300.0, 200.0, 530.0, 280.0), SelectionHandle.LEFT) { box ->
            box.box.right + MIRROR_PAST to box.centerY
        }
        // The last line and the TeX image: larger by a corner, then moved up.
        drag(INK, Region(0.0, 440.0, 560.0, 595.0), SelectionHandle.BOTTOM_RIGHT) { box ->
            box.box.right + 40 to box.box.bottom + 20
        }
        drag(INK, Region(0.0, 430.0, 620.0, 595.0), SelectionHandle.MOVE) { box ->
            box.centerX + 20 to box.centerY - 30
        }
        // The title (text): larger by a corner, and red.
        val title =
            drag(EXTRAS, Region(0.0, 0.0, 842.0, 70.0), SelectionHandle.TOP_LEFT) { box ->
                box.box.left - 20 to box.box.top - 10
            }
        restyle(EXTRAS, title, Color.of(204, 0, 0), null)
        // The figure (an image, with arrows on it): stretched by its right edge, arrows thicker.
        val figure =
            drag(EXTRAS, Region(550.0, 80.0, 800.0, 260.0), SelectionHandle.RIGHT) { box ->
                box.box.right + 40 to box.centerY
            }
        restyle(EXTRAS, figure, null, PenSize.THICK)
        moveToNewPage()
    }

    /** The exercise (text) onto a new page, with two copies left on the way. */
    private fun moveToNewPage() {
        then(PageOperations.insertPage(document, 1, NewPageTemplate().newPage(document.pages[0], 0)))
        val indices = select(0, EXTRAS, Region(550.0, 410.0, 842.0, 470.0))
        val elements = indices.map { document.pages[0].layers[EXTRAS].elements[it] }
        for (step in 1..2) {
            val stamp = SelectionTransform(dx = STAMP_STEP * step, dy = STAMP_STEP * step)
            then(SelectionOperations.add(document, 0, EXTRAS, elements.map(stamp::apply)).edit)
        }
        val moved = SelectionTransform(dx = -400.0, dy = -300.0)
        val target = document.pages[1].layers.lastIndex
        then(
            SelectionOperations
                .moveToPage(document, 0, EXTRAS, indices, 1, target, elements.map(moved::apply))
                .edit,
        )
    }

    private class Region(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
    )

    /** What [region] encloses on layer [layer] of page [page]. */
    private fun select(
        page: Int,
        layer: Int,
        region: Region,
    ): List<Int> {
        val elements = document.pages[page].layers[layer].elements
        val shape = SelectionShape.Rect(region.left, region.top, region.right, region.bottom)
        val indices = Selecting.enclosed(shape, elements, measurer)
        assertTrue("nothing in the region", indices.isNotEmpty())
        return indices
    }

    /** Selects [region] of layer [layer] of the first page and drags [handle] to where [to] says. */
    private fun drag(
        layer: Int,
        region: Region,
        handle: SelectionHandle,
        to: (SelectionBox) -> Pair<Double, Double>,
    ): List<Int> {
        val indices = select(0, layer, region)
        val elements = indices.map { document.pages[0].layers[layer].elements[it] }
        val box =
            SelectionBox(
                checkNotNull(Selecting.bounds(elements, measurer)),
                Selecting.canRotate(elements),
                Selecting.canStretch(elements),
                Selecting.canMirror(elements),
            )
        assertTrue("$handle not shown", handle == SelectionHandle.MOVE || handle in box.handles)
        val (startX, startY) =
            if (handle == SelectionHandle.MOVE) box.centerX to box.centerY else box.position(handle, ROTATE_OFFSET)
        val (x, y) = to(box)
        val transform = box.transform(handle, startX, startY, x, y, MIN_SIZE)
        then(SelectionOperations.replace(document, 0, layer, indices, elements.map(transform::apply)).edit)
        return indices
    }

    private fun restyle(
        layer: Int,
        indices: List<Int>,
        color: Color?,
        size: PenSize?,
    ) {
        val elements = document.pages[0].layers[layer].elements
        val restyled =
            indices.map { index ->
                var element = elements[index]
                color?.let { element = SelectionOperations.recolored(element, it) }
                size?.let { element = SelectionOperations.resized(element, it) }
                element
            }
        then(SelectionOperations.replace(document, 0, layer, indices, restyled).edit)
    }

    private fun then(edit: Edit?) {
        document = document.applying(checkNotNull(edit))
    }

    private companion object {
        const val PATH = "libreta-selection.xopp"
        const val INK = 0
        const val EXTRAS = 1
        const val ROTATE_OFFSET = 20.0
        const val ROTATE_REACH = 100.0
        const val MIRROR_PAST = 80.0
        const val MIN_SIZE = 5.0
        const val STAMP_STEP = 12.0
    }
}
