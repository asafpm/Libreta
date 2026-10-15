// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.view.MotionEvent
import kotlin.math.hypot

/**
 * Taps with the text tool, for [InkInput]: a pointer that lifts where it went down edits text
 * there. A finger that moves instead scrolls (or zooms) the page; a stylus that moves does
 * nothing.
 */
internal class TextTapInput(
    private val host: InkInput.Host,
) {
    private var pointerId: Int? = null
    private var downX = 0f
    private var downY = 0f
    private var byFinger = false

    val isActive: Boolean get() = pointerId != null

    fun begin(
        event: MotionEvent,
        index: Int,
    ) {
        pointerId = event.getPointerId(index)
        downX = event.getX(index)
        downY = event.getY(index)
        byFinger = event.getToolType(index) == MotionEvent.TOOL_TYPE_FINGER
    }

    /** Handles [event] while tapping; returns false once the pointer is done. */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val index = pointerId?.let { event.findPointerIndex(it) } ?: return false
        val moved = index >= 0 && hypot(event.getX(index) - downX, event.getY(index) - downY) > host.touchSlop
        when {
            index < 0 || event.actionMasked == MotionEvent.ACTION_CANCEL -> {
                pointerId = null
            }

            moved && byFinger -> {
                pointerId = null
                host.startGesture(event)
            }

            event.actionMasked == MotionEvent.ACTION_UP ||
                (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.actionIndex == index) -> {
                pointerId = null
                if (!moved) host.tapText(downX, downY)
            }

            else -> {} // moving, or another pointer
        }
        return isActive
    }
}
