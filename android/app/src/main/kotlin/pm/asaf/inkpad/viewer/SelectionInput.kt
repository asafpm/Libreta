// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.view.MotionEvent

/**
 * Selecting for [InkInput]: one pointer selects or drags the selection until it lifts, other
 * pointers (a palm) are ignored. The stylus button's state goes along: pressed while a
 * selection is moved, it stamps a copy.
 */
internal class SelectionInput(
    private val host: InkInput.Host,
) {
    private var pointerId: Int? = null

    val isActive: Boolean get() = pointerId != null

    fun begin(
        event: MotionEvent,
        index: Int,
    ) {
        pointerId = event.getPointerId(index)
        host.selectionTool.down(event.getX(index), event.getY(index), host.tools.selectMode)
    }

    /** Handles [event] while selecting; returns false once the pointer is done. */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val tool = host.selectionTool
        val index = pointerId?.let { event.findPointerIndex(it) } ?: return false
        when {
            index < 0 || event.actionMasked == MotionEvent.ACTION_CANCEL -> {
                pointerId = null
                tool.cancel()
            }

            event.actionMasked == MotionEvent.ACTION_MOVE -> {
                tool.move(
                    event.getX(index),
                    event.getY(index),
                    event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY),
                )
            }

            event.actionMasked == MotionEvent.ACTION_UP ||
                (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.actionIndex == index) -> {
                pointerId = null
                tool.up(event.getX(index), event.getY(index))
            }

            else -> {} // another pointer, e.g. a palm
        }
        return isActive
    }
}
