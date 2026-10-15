// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PageOperationsTest {
    private fun stroke(x: Double) = Element.Stroke(StrokeTool.PEN, Color.of(0, 0, 0), 1.0, PointList.of(x, 0.0, x, 1.0))

    private fun page(
        vararg strokes: Double,
        background: Background? = Background.Solid(Color.WHITE, PageStyle.LINED),
    ) = Page(100.0, 200.0, background, listOf(Layer(strokes.map(::stroke))))

    private val a = page(1.0)
    private val b = page(2.0)
    private val c = page(3.0)
    private val document = Document(listOf(a, b, c))

    /** [edit] done, checking it undoes back to [document]. */
    private fun done(edit: Edit?): Document {
        val after = document.applying(checkNotNull(edit))
        assertEquals(document, after.reverting(edit))
        return after
    }

    @Test
    fun insertsDuplicatesDeletesAndMovesPages() {
        val inserted = done(PageOperations.insertPage(document, 1, page(9.0)))
        assertEquals(listOf(a, page(9.0), b, c), inserted.pages)
        assertContentEquals(intArrayOf(0, -1, 1, 2), pageMapping(document.pages, inserted.pages))

        val duplicated = done(PageOperations.duplicatePage(document, 0))
        assertEquals(listOf(a, a, b, c), duplicated.pages)
        // A copy: the same content, a page of its own.
        assertNotSame(duplicated.pages[0], duplicated.pages[1])
        assertContentEquals(intArrayOf(0, -1, 1, 2), pageMapping(document.pages, duplicated.pages))

        assertEquals(listOf(a, c), done(PageOperations.deletePage(document, 1)).pages)
        assertNull(PageOperations.deletePage(Document(listOf(a)), 0))

        val moved = done(PageOperations.movePage(document, 2, 0))
        assertEquals(listOf(c, a, b), moved.pages)
        assertContentEquals(intArrayOf(2, 0, 1), pageMapping(document.pages, moved.pages))
        assertNull(PageOperations.movePage(document, 1, 1))
        assertNull(PageOperations.movePage(document, 1, 3))
    }

    @Test
    fun setsBackgrounds() {
        val graph = Background.Solid(Color.of(255, 255, 200), PageStyle.GRAPH)
        val after = done(PageOperations.setBackground(document, listOf(0, 2), graph))
        assertEquals(listOf(graph, a.background, graph), after.pages.map { it.background })
        assertSame(b, after.pages[1])
        assertNull(PageOperations.setBackground(after, listOf(0), graph))
    }

    @Test
    fun changesLayers() {
        val added = done(LayerOperations.addLayer(document, 1, 1))
        assertEquals(listOf(1, 0), added.pages[1].layers.map { it.elements.size })
        assertSame(a, added.pages[0])

        val named = added.applying(LayerOperations.renameLayer(added, 1, 1, "Notes"))
        assertEquals(listOf(null, "Notes"), named.pages[1].layers.map { it.name })
        assertEquals(
            null,
            named
                .applying(LayerOperations.renameLayer(named, 1, 1, "  "))
                .pages[1]
                .layers[1]
                .name,
        )

        val moved = named.applying(checkNotNull(LayerOperations.moveLayer(named, 1, 1, 0)))
        assertEquals(listOf("Notes", null), moved.pages[1].layers.map { it.name })

        val withInk = moved.withPage(1, moved.pages[1].withElementAdded(stroke(5.0), 1))
        val merged = withInk.applying(checkNotNull(LayerOperations.mergeLayerDown(withInk, 1, 1)))
        // The upper layer's elements go on top of the lower one's.
        assertEquals(
            listOf(stroke(2.0), stroke(5.0)),
            merged.pages[1]
                .layers
                .single()
                .elements,
        )
        assertEquals(
            "Notes",
            merged.pages[1]
                .layers
                .single()
                .name,
        )
        assertNull(LayerOperations.mergeLayerDown(withInk, 1, 0))

        assertNull(LayerOperations.removeLayer(document, 0, 0))
        assertEquals(
            1,
            added
                .applying(checkNotNull(LayerOperations.removeLayer(added, 1, 0)))
                .pages[1]
                .layers.size,
        )
    }

    @Test
    fun theFirstPdfPageNamesThePdf() {
        val first = page(background = Background.Pdf(1, FileDomain.ATTACH, "bg.pdf"))
        val second = page(background = Background.Pdf(2))
        val pdf = Document(listOf(first, second))
        val moved = pdf.applying(checkNotNull(PageOperations.movePage(pdf, 1, 0)))
        assertEquals(
            listOf(Background.Pdf(2, FileDomain.ATTACH, "bg.pdf"), Background.Pdf(1)),
            moved.pages.map { it.background },
        )
        val duplicated = pdf.applying(PageOperations.duplicatePage(pdf, 0))
        assertEquals(listOf("bg.pdf", null, null), duplicated.pages.map { (it.background as Background.Pdf).filename })
    }

    @Test
    fun aNewBackgroundOnTheFirstPdfPageKeepsThePdfNamed() {
        val pdf =
            Document(
                listOf(
                    page(background = Background.Pdf(1, FileDomain.ABSOLUTE, "paper.pdf")),
                    page(background = Background.Pdf(2)),
                    page(background = Background.Pdf(3)),
                ),
            )
        val lined = Background.Solid(Color.WHITE, PageStyle.LINED)
        val changed = pdf.applying(checkNotNull(PageOperations.setBackground(pdf, listOf(0), lined)))
        assertEquals(
            listOf(lined, Background.Pdf(2, FileDomain.ABSOLUTE, "paper.pdf"), Background.Pdf(3)),
            changed.pages.map { it.background },
        )
        // Undone, as it was.
        assertEquals(pdf, changed.reverting(checkNotNull(PageOperations.setBackground(pdf, listOf(0), lined))))
    }

    @Test
    fun aNewBackgroundOnAClonedImageSourceLeavesTheCloneItsImage() {
        val image = page(background = Background.Pixmap(FileDomain.ATTACH, "bg_1.png"))
        val clone = page(background = Background.Pixmap(FileDomain.CLONE, "0"))
        val doc = Document(listOf(image, clone))
        val lined = Background.Solid(Color.WHITE, PageStyle.LINED)
        val changed = doc.applying(checkNotNull(PageOperations.setBackground(doc, listOf(0), lined)))
        assertEquals(listOf(lined, image.background), changed.pages.map { it.background })
    }

    @Test
    fun clonedImagesFollowTheirSource() {
        val image = page(background = Background.Pixmap(FileDomain.ATTACH, "bg_1.png"))
        val clone = page(background = Background.Pixmap(FileDomain.CLONE, "1"))
        val doc = Document(listOf(a, image, clone))
        // The source moves first: the clone points at its new place.
        val sourceFirst = doc.applying(checkNotNull(PageOperations.movePage(doc, 1, 0)))
        assertEquals(Background.Pixmap(FileDomain.CLONE, "0"), sourceFirst.pages[2].background)
        // The source is gone, or now after it: the clone takes its image.
        val deleted = doc.applying(checkNotNull(PageOperations.deletePage(doc, 1)))
        assertEquals(image.background, deleted.pages[1].background)
        val after = doc.applying(checkNotNull(PageOperations.movePage(doc, 2, 0)))
        assertEquals(image.background, after.pages[0].background)
        assertEquals(image.background, after.pages[2].background)
    }

    @Test
    fun newPagesCopyTheCurrentOneOrUseTheTemplate() {
        val copying = NewPageTemplate()
        val graph = page(background = Background.Solid(Color.of(1, 2, 3), PageStyle.GRAPH, config = "spacing=5"))
        val copied = copying.newPage(graph, 4)
        assertEquals(Page(100.0, 200.0, graph.background, listOf(Layer(emptyList()))), copied)
        val image = page(background = Background.Pixmap(FileDomain.ATTACH, "bg_1.png"))
        assertEquals(Background.Pixmap(FileDomain.CLONE, "4"), copying.newPage(image, 4).background)
        val pdf = page(background = Background.Pdf(3, FileDomain.ATTACH, "bg.pdf"))
        assertEquals(Background.Pdf(3), copying.newPage(pdf, 4).background)

        val template =
            NewPageTemplate(
                copyCurrent = false,
                PaperSize.LETTER,
                landscape = true,
                PageStyle.DOTTED,
                Color.of(255, 255, 0),
            )
        val fromTemplate = template.newPage(graph, 4)
        assertEquals(792.0, fromTemplate.width)
        assertEquals(612.0, fromTemplate.height)
        assertEquals(Background.Solid(Color.of(255, 255, 0), PageStyle.DOTTED), fromTemplate.background)
        // A note's first page always comes from the template's paper.
        assertTrue(copying.newPage(null, -1).background == Background.Solid(Color.WHITE, PageStyle.LINED))
    }
}
