// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.view.MotionEvent
import pm.asaf.libreta.core.model.EraserType
import pm.asaf.libreta.core.model.StylusButton
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.ToolSettings

/**
 * The tool pointer [index] of [event] uses: the stylus's eraser end erases, holding its side
 * button uses the tool set for it, and otherwise the chosen tool.
 */
internal fun ToolSettings.toolFor(
    event: MotionEvent,
    index: Int,
): Tool {
    val type = event.getToolType(index)
    val buttonHeld = type == MotionEvent.TOOL_TYPE_STYLUS && event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)
    return when {
        type == MotionEvent.TOOL_TYPE_ERASER -> Tool.ERASER
        buttonHeld && button == StylusButton.ERASER -> Tool.ERASER
        buttonHeld && button == StylusButton.HIGHLIGHTER -> Tool.HIGHLIGHTER
        else -> tool
    }
}

/** Erasing for [InkInput]: one pointer erases until it lifts, other pointers (a palm) are ignored. */
internal class EraserInput(
    private val host: InkInput.Host,
) : PointerInput {
    private var pointerId: Int? = null

    override val isActive: Boolean get() = pointerId != null

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
    override fun onTouchEvent(event: MotionEvent): Boolean {
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
