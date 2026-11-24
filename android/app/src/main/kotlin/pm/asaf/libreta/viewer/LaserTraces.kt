// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.graphics.Canvas
import pm.asaf.libreta.core.model.Element
import kotlin.math.roundToInt

/**
 * The laser pointer's traces: strokes on pages, shown over them and never kept. Like Xournal++'s
 * laser pointer, they stay while it is used, and all fade out together once it has rested for its
 * fade delay. Times are [android.os.SystemClock.uptimeMillis].
 */
internal class LaserTraces {
    private val traces = ArrayList<Pair<Int, Element.Stroke>>()

    /** Whether a trace is being drawn: none fades meanwhile. */
    private var pointing = false

    /** When the traces start to fade. */
    private var fadeAt = 0L

    /** A trace is being drawn. */
    fun start() {
        pointing = true
    }

    /**
     * The trace being drawn ended at [now], leaving [stroke] on [page] (null: it was dropped);
     * the traces fade [fadeDelayMillis] later unless another is drawn.
     */
    fun finish(
        page: Int,
        stroke: Element.Stroke?,
        now: Long,
        fadeDelayMillis: Long,
    ) {
        pointing = false
        fadeAt = now + fadeDelayMillis
        stroke?.let { traces += page to it }
    }

    fun clear() {
        traces.clear()
        pointing = false
    }

    /** How opaque the traces are at [now], from 1 down to 0 as they fade. */
    fun opacity(now: Long): Float {
        val fading = now - fadeAt
        return when {
            pointing || fading <= 0 -> 1f
            fading >= FADE_MILLIS -> 0f
            else -> 1f - fading.toFloat() / FADE_MILLIS
        }
    }

    /**
     * Draws the traces as they are at [now] with [drawTrace]. Returns whether they are still
     * shown, and so change later: draw again.
     */
    fun draw(
        canvas: Canvas,
        now: Long,
        drawTrace: (page: Int, stroke: Element.Stroke) -> Unit,
    ): Boolean {
        val opacity = opacity(now)
        if (opacity <= 0f) traces.clear()
        if (traces.isEmpty()) return false
        val layer = if (opacity < 1f) canvas.saveLayerAlpha(null, (opacity * OPAQUE).roundToInt()) else null
        for ((page, stroke) in traces) drawTrace(page, stroke)
        layer?.let(canvas::restoreToCount)
        return true
    }

    private companion object {
        /** How long fading out takes. */
        const val FADE_MILLIS = 400L

        const val OPAQUE = 255
    }
}
