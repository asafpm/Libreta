// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import pm.asaf.inkpad.core.model.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/** Touches injected into [view] on the main thread at about the stylus's event rate, and screenshots of it. */
internal class TestInput(
    private val instrumentation: Instrumentation,
    private val view: View,
) {
    /**
     * A touch by [tool] through [points] (view pixels), an event every [eventMs], held [holdMs]
     * before lifting; [buttons] gives each point's button state. Returns how long the view took
     * over each event, in milliseconds.
     */
    fun touch(
        points: List<Pair<Float, Float>>,
        tool: Int = MotionEvent.TOOL_TYPE_STYLUS,
        eventMs: Long = EVENT_MS,
        holdMs: Long = 0,
        buttons: (Int) -> Int = { 0 },
    ): List<Double> {
        val down = SystemClock.uptimeMillis()
        return points.mapIndexed { index, (x, y) ->
            val action =
                when (index) {
                    0 -> MotionEvent.ACTION_DOWN
                    points.lastIndex -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
            if (index > 0) SystemClock.sleep(if (index == points.lastIndex) eventMs + holdMs else eventMs)
            val start = System.nanoTime()
            send(down, action, x, y, tool, buttons(index))
            (System.nanoTime() - start) / NANOS_PER_MS
        }
    }

    /** Whether [color] is at view column [x], within [search] pixels of row [y], in a screenshot. */
    fun colorOnScreen(
        color: Color,
        x: Float,
        y: Float,
        search: Int = SEARCH,
    ): Boolean {
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        val shot: Bitmap = instrumentation.uiAutomation.takeScreenshot()
        val column = (location[0] + x.roundToInt()).coerceIn(0, shot.width - 1)
        return (-search..search).any { dy ->
            val pixel = shot.getPixel(column, (location[1] + y.roundToInt() + dy).coerceIn(0, shot.height - 1))
            abs(android.graphics.Color.red(pixel) - color.red) < COLOR_TOLERANCE &&
                abs(android.graphics.Color.green(pixel) - color.green) < COLOR_TOLERANCE &&
                abs(android.graphics.Color.blue(pixel) - color.blue) < COLOR_TOLERANCE
        }
    }

    private fun send(
        downTime: Long,
        action: Int,
        x: Float,
        y: Float,
        tool: Int,
        buttons: Int,
    ) {
        val properties =
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = tool
            }
        val coordinates =
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = PRESSURE
            }
        val source =
            if (tool ==
                MotionEvent.TOOL_TYPE_FINGER
            ) {
                InputDevice.SOURCE_TOUCHSCREEN
            } else {
                InputDevice.SOURCE_STYLUS
            }
        val event =
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                1,
                arrayOf(properties),
                arrayOf(coordinates),
                0,
                buttons,
                1f,
                1f,
                0,
                0,
                source,
                0,
            )
        instrumentation.runOnMainSync { view.dispatchTouchEvent(event) }
        event.recycle()
    }

    companion object {
        /** About 240 events a second, like the S Pen. */
        const val EVENT_MS = 4L
        const val PRESSURE = 0.6f
        const val SEARCH = 20
        const val COLOR_TOLERANCE = 40
        const val NANOS_PER_MS = 1_000_000.0
    }
}
