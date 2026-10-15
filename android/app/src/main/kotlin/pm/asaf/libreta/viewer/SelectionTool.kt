// SPDX-License-Identifier: GPL-2.0-or-later
// Selecting follows Xournal++ v1.3.8 src/core/control/tools/Selector.cpp and
// src/core/gui/PageView.cpp (a tap picks the nearest object), © Xournal++ contributors.

package pm.asaf.libreta.viewer

import android.graphics.Canvas
import android.graphics.RectF
import pm.asaf.libreta.android.render.PageRenderer
import pm.asaf.libreta.core.geometry.Selecting
import pm.asaf.libreta.core.geometry.SelectionBox
import pm.asaf.libreta.core.geometry.SelectionShape
import pm.asaf.libreta.core.geometry.TextMeasurer
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.SelectMode
import kotlin.math.hypot

/** What is selected: elements [indices] (as [elements]) of layer [layer] of page [page], in [box]. */
internal class Selected(
    val page: Int,
    val layer: Int,
    val indices: List<Int>,
    val elements: List<Element>,
    val box: SelectionBox,
) {
    companion object {
        /** A selection of [indices] of [page]'s [layer], or null if they draw nothing. */
        fun of(
            index: Int,
            page: Page,
            layer: Int,
            indices: List<Int>,
            measurer: TextMeasurer,
        ): Selected? {
            val elements = indices.map { page.layers[layer].elements[it] }
            // Nothing, or nothing drawn: no selection.
            val bounds = Selecting.bounds(elements, measurer) ?: return null
            val box =
                SelectionBox(
                    bounds,
                    Selecting.canRotate(elements),
                    Selecting.canStretch(elements),
                    Selecting.canMirror(elements),
                )
            return Selected(index, layer, indices, elements, box)
        }
    }
}

/**
 * The selection tool on a [DocumentView]: a rectangle or lasso drawn on a page selects what it
 * encloses on the page's selected layer; a tap picks what is under it or nearest; dragging the
 * selection's box moves, scales or rotates it ([SelectionGesture]).
 */
internal class SelectionTool(
    private val host: Host,
) {
    interface Host {
        /** The page under a view point, placed where it is drawn. */
        fun pageAt(
            viewX: Float,
            viewY: Float,
        ): PageMapping?

        /** Where page [index] is drawn. */
        fun mappingOf(index: Int): PageMapping?

        /** The document's pages. */
        val pages: List<Page>

        /** The document's page [index]. */
        fun page(index: Int): Page

        /** The layer of page [index] that selection works on. */
        fun layer(index: Int): Int

        /** Draws the selection's elements, and measures text. */
        val renderer: PageRenderer?

        /** Leaves a selection out of rendering (null: none), or brings it back. */
        fun hide(hidden: HiddenElements?)

        /** Does [edit] and shows it; [hidden] (a selection being moved) is shown again as part of it. */
        fun edit(edit: Edit)

        /**
         * Layout offset from page [from] to page [to], in points: what to add to a position on
         * [from] to have it on [to].
         */
        fun offset(
            from: Int,
            to: Int,
        ): Pair<Double, Double>

        /** The part of page [index] on screen, in page units; null if none. */
        fun visibleArea(index: Int): Bounds?

        fun redraw()

        /** The selection changed (or went). */
        fun changed()
    }

    /** A rectangle or lasso being drawn on page [mapping], points in view pixels. */
    private class Area(
        val mapping: PageMapping,
        val mode: SelectMode,
    ) {
        val points = ArrayList<Float>()
    }

    var selected: Selected? = null
        private set
    private var area: Area? = null
    private var gesture: SelectionGesture? = null
    private val overlay = SelectionOverlay()

    /** Pages are shown dark: a moving selection's picture is recoloured like them. */
    var darkPages: Boolean
        get() = overlay.darkPages
        set(value) {
            overlay.darkPages = value
        }
    private val image = KeptSelectionImage(host)

    /** Handle sizes, in view pixels. */
    var density: Float = 1f

    /** A touch at a view point: takes the box, or starts selecting anew. */
    fun down(
        viewX: Float,
        viewY: Float,
        mode: SelectMode,
    ) {
        val current = selected
        val mapping = current?.let { host.mappingOf(it.page) }
        val handle =
            if (current != null && mapping != null) {
                current.box.handleAt(
                    mapping.toPageX(viewX),
                    mapping.toPageY(viewY),
                    handleRadius() / mapping.zoom,
                    rotateOffset() / mapping.zoom,
                )
            } else {
                null
            }
        if (current != null && mapping != null && handle != null) {
            gesture =
                SelectionGesture(
                    current,
                    handle,
                    mapping,
                    viewX,
                    viewY,
                    host,
                ).also { it.start(MIN_SIZE / mapping.zoom, image.of(current, mapping.zoom)) }
            host.redraw()
            return
        }
        clear()
        area = host.pageAt(viewX, viewY)?.let { Area(it, mode) }
        area?.points?.addAll(listOf(viewX, viewY))
    }

    /** The touch moved; [button] is whether the stylus button is held (it stamps copies of a moved selection). */
    fun move(
        viewX: Float,
        viewY: Float,
        button: Boolean,
    ) {
        gesture?.moveTo(viewX, viewY, button)
        area?.points?.addAll(listOf(viewX, viewY))
        host.redraw()
    }

    fun up(
        viewX: Float,
        viewY: Float,
    ) {
        gesture?.let { moving ->
            gesture = null
            moving.commit(viewX, viewY)?.let {
                if (!moving.moved) image.forget()
                selected = it
            }
            host.changed()
        }
        area?.let { drawn ->
            area = null
            finish(drawn)
        }
        image.prepare(selected)
        host.redraw()
    }

    /** The touch was cancelled: a moved selection goes back. */
    fun cancel() {
        gesture?.let {
            gesture = null
            it.abandon()
        }
        area = null
        host.redraw()
    }

    /** Nothing selected any more. */
    fun clear() {
        cancel()
        image.forget()
        if (selected != null) {
            selected = null
            host.changed()
        }
    }

    /** Selects [indices] of [page]'s selected layer (e.g. pasted elements). */
    fun select(
        page: Int,
        indices: List<Int>,
    ) {
        val layer = host.layer(page)
        val measurer = host.renderer?.textMeasurer ?: return
        selected = Selected.of(page, host.page(page), layer, indices, measurer)
        image.forget()
        image.prepare(selected)
        host.changed()
    }

    /** The selection's box on screen, in view pixels; null if nothing is selected. */
    fun screenBounds(): RectF? {
        val current = selected ?: return null
        val box = current.box.box
        return host.mappingOf(current.page)?.let {
            RectF(it.toViewX(box.left), it.toViewY(box.top), it.toViewX(box.right), it.toViewY(box.bottom))
        }
    }

    fun draw(canvas: Canvas) {
        area?.let { overlay.drawArea(canvas, it.points, it.mode) }
        val gesture = gesture
        val current = selected
        if (gesture != null) {
            gesture.draw(canvas, overlay)
        } else if (current != null) {
            host
                .mappingOf(
                    current.page,
                )?.let { overlay.drawBox(canvas, current.box, it, handleRadius(), rotateOffset()) }
        }
    }

    /** What [drawn] encloses, or after a tap, what is under it or nearest. */
    private fun finish(drawn: Area) {
        val points = drawn.points
        val mapping = drawn.mapping
        val index = mapping.page
        val layer = host.layer(index)
        val elements = host.page(index).layers[layer].elements
        val measurer = host.renderer?.textMeasurer ?: return
        val reach =
            (0 until points.size / 2).maxOf {
                hypot(
                    points[2 * it] - points[0],
                    points[2 * it + 1] - points[1],
                )
            }
        val indices =
            if (reach < TAP_PX * density) {
                listOfNotNull(
                    Selecting.picked(
                        elements,
                        mapping.toPageX(points[0]),
                        mapping.toPageY(points[1]),
                        PICK_RADIUS,
                        measurer,
                    ),
                )
            } else {
                val shape =
                    when (drawn.mode) {
                        SelectMode.RECTANGLE -> {
                            SelectionShape.Rect(
                                mapping.toPageX(points[0]),
                                mapping.toPageY(points[1]),
                                mapping.toPageX(points[points.size - 2]),
                                mapping.toPageY(points[points.size - 1]),
                            )
                        }

                        SelectMode.LASSO -> {
                            SelectionShape.Lasso(
                                DoubleArray(points.size) {
                                    if (it % 2 ==
                                        0
                                    ) {
                                        mapping.toPageX(points[it])
                                    } else {
                                        mapping.toPageY(points[it])
                                    }
                                },
                            )
                        }
                    }
                Selecting.enclosed(shape, elements, measurer)
            }
        selected = Selected.of(index, host.page(index), layer, indices, measurer)
        image.forget()
        host.changed()
    }

    private fun handleRadius() = HANDLE_DP * density / 2 + 2 * density

    private fun rotateOffset() = (HANDLE_DP + ROTATE_GAP_DP) * density

    private companion object {
        /** A touch moving less than this (dp) is a tap. */
        const val TAP_PX = 10f

        /** A tap picks within this (points), like Xournal++. */
        const val PICK_RADIUS = 5.0

        /** The smallest a scaled selection gets, in view pixels. */
        const val MIN_SIZE = 5.0
        const val HANDLE_DP = 14f
        const val ROTATE_GAP_DP = 16f
    }
}
