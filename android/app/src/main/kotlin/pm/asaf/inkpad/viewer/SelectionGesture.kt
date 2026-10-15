// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8 src/core/control/tools/EditSelection.cpp (dragging the box; dropping
// it on another page; a drop off any page goes back), © Xournal++ contributors.

package pm.asaf.inkpad.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import pm.asaf.inkpad.core.geometry.SelectionHandle
import pm.asaf.inkpad.core.geometry.SelectionTransform
import pm.asaf.inkpad.core.model.Document
import pm.asaf.inkpad.core.model.Edit
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.SelectionOperations
import pm.asaf.inkpad.core.model.applying
import pm.asaf.inkpad.core.model.followedBy
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * [selected] held by [handle] at a view point and dragged: moved (onto another page too),
 * scaled or rotated. Meanwhile the elements are left out of their page and drawn from a
 * bitmap, transformed. Pressing the stylus button while moving leaves a copy where the
 * selection is. Released, it is one edit.
 */
internal class SelectionGesture(
    private val selected: Selected,
    private val handle: SelectionHandle,
    /** Where the selection's page is drawn, as the drag started. */
    private val mapping: PageMapping,
    private val startViewX: Float,
    private val startViewY: Float,
    private val host: SelectionTool.Host,
) {
    /** A copy left behind: how it is transformed, and the page it goes on. */
    private class Stamp(
        val transform: SelectionTransform,
        val page: Int,
    )

    private var minSize = 0.0
    private var transform = SelectionTransform()

    /** The page a moved selection goes on: the one under the finger (null: none, it goes back). */
    private var target: Int? = selected.page
    private val stamps = ArrayList<Stamp>()
    private var button = false
    private var bitmap: Bitmap? = null
    private var bitmapZoom = 1f

    /** Takes the elements off their page, into a bitmap; the box keeps at least [minSize] (points). */
    fun start(minSize: Double) {
        this.minSize = minSize
        host.hide(HiddenElements(selected.page, selected.layer, selected.indices.toSet()))
        val renderer = host.renderer ?: return
        val box = selected.box.box
        val width = box.right - box.left
        val height = box.bottom - box.top
        // Sharp at the current zoom, unless that would be huge.
        val zoom = min(mapping.zoom, sqrt(MAX_PIXELS / (width * height))).toFloat()
        bitmapZoom = zoom
        bitmap =
            Bitmap
                .createBitmap(
                    ceil(width * zoom).toInt().coerceAtLeast(1),
                    ceil(height * zoom).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                ).also {
                    val canvas = Canvas(it)
                    canvas.translate((-box.left * zoom).toFloat(), (-box.top * zoom).toFloat())
                    renderer.renderElements(selected.elements, canvas, zoom)
                }
    }

    /** The finger is at a view point; [button]: the stylus button is held (pressing it stamps a copy). */
    fun moveTo(
        viewX: Float,
        viewY: Float,
        button: Boolean,
    ) {
        transform =
            selected.box.transform(
                handle,
                mapping.toPageX(startViewX),
                mapping.toPageY(startViewY),
                mapping.toPageX(viewX),
                mapping.toPageY(viewY),
                minSize,
            )
        if (handle == SelectionHandle.MOVE) {
            target = host.pageAt(viewX, viewY)?.page
            val page = target
            if (button && !this.button && page != null) stamps += Stamp(transform, page)
        }
        this.button = button
    }

    /** Released at a view point: the edit made, and the selection after it (null: nothing changed). */
    fun commit(
        viewX: Float,
        viewY: Float,
    ): Selected? {
        moveTo(viewX, viewY, button)
        val page = target
        if (page == null || (transform == SelectionTransform() && stamps.isEmpty())) {
            abandon()
            return null
        }
        return done(page)
    }

    /** The edit for the selection placed on [page], with the copies stamped; done. */
    private fun done(page: Int): Selected? {
        var document = Document(host.pages)
        var edit: Edit? = null
        for (stamp in stamps) {
            val added =
                SelectionOperations.add(
                    document,
                    stamp.page,
                    host.layer(stamp.page),
                    placed(stamp.transform, stamp.page),
                )
            document = document.applying(added.edit)
            edit = edit?.followedBy(added.edit) ?: added.edit
        }
        val moved = placed(transform, page)
        val result =
            if (page == selected.page) {
                SelectionOperations.replace(document, page, selected.layer, selected.indices, moved)
            } else {
                SelectionOperations.moveToPage(
                    document,
                    selected.page,
                    selected.layer,
                    selected.indices,
                    page,
                    host.layer(page),
                    moved,
                )
            }
        val done = edit?.followedBy(result.edit) ?: result.edit
        host.edit(done)
        bitmap = null
        val measurer = host.renderer?.textMeasurer ?: return null
        return Selected.of(
            result.page,
            document.applying(result.edit).pages[result.page],
            result.layer,
            result.indices,
            measurer,
        )
    }

    /** Nothing done: the elements go back to their page. */
    fun abandon() {
        bitmap = null
        host.hide(null)
    }

    fun draw(
        canvas: Canvas,
        overlay: SelectionOverlay,
    ) {
        val image = bitmap
        for (stamp in stamps) overlay.drawMoving(canvas, image, bitmapZoom, selected.box, mapping, stamp.transform)
        overlay.drawMoving(canvas, image, bitmapZoom, selected.box, mapping, transform)
    }

    /** The elements transformed by [transform], placed on page [page]. */
    private fun placed(
        transform: SelectionTransform,
        page: Int,
    ): List<Element> {
        val (dx, dy) = host.offset(selected.page, page)
        val there = transform.copy(dx = transform.dx + dx, dy = transform.dy + dy)
        return selected.elements.map(there::apply)
    }

    private companion object {
        /** The most pixels a selection's bitmap has. */
        const val MAX_PIXELS = 16_000_000.0
    }
}
