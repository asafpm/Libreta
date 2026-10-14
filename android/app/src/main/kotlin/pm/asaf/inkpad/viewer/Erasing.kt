// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.graphics.Canvas
import android.graphics.Paint
import pm.asaf.inkpad.core.geometry.PageEraser
import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Edit
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.model.PageChange
import kotlin.math.roundToInt

/**
 * Eraser gestures: each one erases on the pages it goes over ([PageEraser]) and is one edit,
 * shown as it goes. A square cursor shows what the eraser covers.
 */
internal class Erasing(
    private val host: Host,
) {
    interface Host {
        /** The page under a view point, or null if there is none. */
        fun pageAt(
            viewX: Float,
            viewY: Float,
        ): PageMapping?

        /** The page [index] as shown now. */
        fun page(index: Int): Page

        /** Page [index] is now [page], changed within [changed] (page units): show it. */
        fun show(
            index: Int,
            page: Page,
            changed: Bounds,
        )

        /** A gesture erased something. */
        fun edited(edit: Edit)
    }

    /** One gesture: the pages it erased on, and where it was last. */
    private class Gesture(
        val deleteStrokes: Boolean,
        val halfSize: Double,
    ) {
        val erasers = LinkedHashMap<Int, PageEraser>()
        var lastPage = -1
        var lastX = 0.0
        var lastY = 0.0
    }

    /** The eraser square's centre and half side, in view pixels. */
    private class Cursor(
        val x: Float,
        val y: Float,
        val halfSize: Float,
    )

    private var gesture: Gesture? = null
    private var cursor: Cursor? = null
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    val isActive: Boolean get() = gesture != null

    fun start(
        deleteStrokes: Boolean,
        halfSize: Double,
    ) {
        gesture = Gesture(deleteStrokes, halfSize)
    }

    fun eraseAt(
        viewX: Float,
        viewY: Float,
    ) {
        val gesture = gesture ?: return
        val mapping = host.pageAt(viewX, viewY)
        cursor = mapping?.let { Cursor(viewX, viewY, (gesture.halfSize * it.zoom).toFloat()) }
        if (mapping == null) {
            gesture.lastPage = -1
            return
        }
        val index = mapping.page
        val x = mapping.toPageX(viewX)
        val y = mapping.toPageY(viewY)
        val eraser = gesture.erasers.getOrPut(index) { PageEraser(host.page(index), gesture.deleteStrokes) }
        val changed =
            if (gesture.lastPage == index) {
                eraser.eraseAlong(gesture.lastX, gesture.lastY, x, y, gesture.halfSize)
            } else {
                eraser.erase(x, y, gesture.halfSize)
            }
        gesture.lastPage = index
        gesture.lastX = x
        gesture.lastY = y
        if (changed != null) host.show(index, eraser.page, changed)
    }

    /** Ends the gesture: what it erased becomes one edit. */
    fun finish() {
        val gesture = gesture ?: return
        this.gesture = null
        cursor = null
        val changes =
            gesture.erasers
                .filterValues { it.page !== it.original }
                .map { (index, eraser) -> PageChange(index, eraser.original, eraser.page) }
        if (changes.isNotEmpty()) host.edited(Edit(changes))
    }

    /** Shows the cursor at a view point while the stylus hovers, for an eraser of [halfSize] points; null hides it. */
    fun hover(
        viewX: Float,
        viewY: Float,
        halfSize: Double?,
    ) {
        if (gesture != null) return
        val mapping = halfSize?.let { host.pageAt(viewX, viewY) }
        cursor = mapping?.let { Cursor(viewX, viewY, (halfSize * it.zoom).toFloat()) }
    }

    /** Draws the cursor, if shown: a dark square with a light rim, visible on any page. */
    fun drawCursor(canvas: Canvas) {
        val cursor = cursor ?: return
        // On pixel centres: crisp lines.
        val left = (cursor.x - cursor.halfSize).roundToInt() + HALF_PIXEL
        val top = (cursor.y - cursor.halfSize).roundToInt() + HALF_PIXEL
        val right = (cursor.x + cursor.halfSize).roundToInt() + HALF_PIXEL
        val bottom = (cursor.y + cursor.halfSize).roundToInt() + HALF_PIXEL
        outline.color = RIM
        outline.strokeWidth = RIM_WIDTH
        canvas.drawRect(left, top, right, bottom, outline)
        outline.color = LINE
        outline.strokeWidth = LINE_WIDTH
        canvas.drawRect(left, top, right, bottom, outline)
    }

    private companion object {
        const val RIM = 0xC0FFFFFF.toInt()
        const val LINE = 0xFF404040.toInt()
        const val RIM_WIDTH = 3f
        const val LINE_WIDTH = 1f
        const val HALF_PIXEL = 0.5f
    }
}
