// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.view.MotionEvent
import pm.asaf.inkpad.core.model.EraserType
import pm.asaf.inkpad.core.model.StylusButton
import pm.asaf.inkpad.core.model.Tool
import pm.asaf.inkpad.core.model.ToolSettings

/**
 * Whether pointer [index] of [event] erases: the stylus's eraser end does, so does holding
 * its side button unless that is turned off, and otherwise the chosen tool decides.
 */
internal fun ToolSettings.erases(
    event: MotionEvent,
    index: Int,
): Boolean {
    val type = event.getToolType(index)
    val buttonHeld = type == MotionEvent.TOOL_TYPE_STYLUS && event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)
    return type == MotionEvent.TOOL_TYPE_ERASER || (buttonHeld && button == StylusButton.ERASER) || tool == Tool.ERASER
}

/** Erasing for [InkInput]: one pointer erases until it lifts, other pointers (a palm) are ignored. */
internal class EraserInput(
    private val host: InkInput.Host,
) {
    private var pointerId: Int? = null

    val isActive: Boolean get() = pointerId != null

    /** Starts erasing with pointer [index] of [event]. */
    fun begin(
        event: MotionEvent,
        index: Int,
    ) {
        val eraser = host.tools.eraser
        pointerId = event.getPointerId(index)
        host.startErasing(eraser.type == EraserType.DELETE_STROKE, eraser.width)
        host.eraseAt(event.getX(index), event.getY(index))
    }

    /** Handles [event] while erasing; returns false once erasing has ended. */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val index = pointerId?.let { event.findPointerIndex(it) } ?: return false
        when {
            // Kept even when cancelled: the erasing was seen as it went.
            index < 0 || event.actionMasked == MotionEvent.ACTION_CANCEL -> {
                finish()
            }

            event.actionMasked == MotionEvent.ACTION_MOVE -> {
                for (h in 0 until event.historySize) {
                    host.eraseAt(event.getHistoricalX(index, h), event.getHistoricalY(index, h))
                }
                host.eraseAt(event.getX(index), event.getY(index))
            }

            event.actionMasked == MotionEvent.ACTION_UP ||
                (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.actionIndex == index) -> {
                host.eraseAt(event.getX(index), event.getY(index))
                finish()
            }

            else -> {} // another pointer, e.g. a palm
        }
        return isActive
    }

    private fun finish() {
        pointerId = null
        host.finishErasing()
    }
}
