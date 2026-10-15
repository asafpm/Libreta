// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.android.render.RenderCaches
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.core.model.Edit
import pm.asaf.inkpad.core.model.SelectMode
import pm.asaf.inkpad.core.model.Tool
import java.util.Collections
import kotlin.math.roundToInt

/**
 * Selecting most of a busy page of the 104-page note and dragging it: frames stay fast while
 * the selection moves, scales and stamps copies, and each drag is one edit. Writes
 * selection-report.txt and selection-overlay.png (TestStorage).
 */
@RunWith(AndroidJUnit4::class)
class SelectionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @Test
    fun draggingALargeSelectionIsSmooth() {
        val edits = Collections.synchronizedList(ArrayList<Edit>())
        val (scenario, view) = open(edits)
        val input = TestInput(instrumentation, view)

        val corner = view.width * (1 - MARGIN) to view.height * BOTTOM
        input.touch(line(view.width * MARGIN to view.height * MARGIN, corner))
        SystemClock.sleep(SETTLE_MS)
        val selected = onMain { view.selection.elements.size }
        check(selected > 0) { "nothing selected" }
        TestStorage().openOutputFile("selection-overlay.png").use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
        }

        val frames = Collections.synchronizedList(ArrayList<Long>())
        val metricsThread = HandlerThread("frame-metrics").apply { start() }
        val listener =
            Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            }
        scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(metricsThread.looper)) }
        // Back and forth, then with two copies stamped, then shrunk by its corner.
        val step = view.width * STEP
        val drags = ArrayList<List<Double>>()
        for (direction in listOf(1, -1, 1, -1)) {
            val box = bounds(view)
            val to = box.centerX() + direction * step to box.centerY() + step
            drags += input.touch(line(box.centerX() to box.centerY(), to))
        }
        val box = bounds(view)
        drags +=
            input.touch(line(box.centerX() to box.centerY(), box.centerX() to box.centerY() - 2 * step)) { index ->
                if (index in STAMP_FIRST || index in STAMP_SECOND) MotionEvent.BUTTON_STYLUS_PRIMARY else 0
            }
        val stamped = onMain { view.selection.elements.size }
        val scaled = bounds(view)
        drags += input.touch(line(scaled.right to scaled.bottom, scaled.right - step to scaled.bottom - step))
        SystemClock.sleep(SETTLE_MS)
        scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
        metricsThread.quitSafely()

        val durations = synchronized(frames) { frames.map { it / NANOS_PER_MS } }.sorted()
        val report =
            buildString {
                val build = if (debuggable) "debuggable" else "release-like"
                appendLine("Selected $selected elements; $DRAGS drags ($build build)")
                appendLine("Edits: ${edits.size}, pages per edit: ${edits.map { it.changes.size }}")
                appendLine("Selected after stamping: $stamped")
                appendLine("Taking the selection (ms): ${drags.map { "%.1f".format(it.first()) }}")
                appendLine("Moving, slowest event (ms): ${drags.map { "%.1f".format(it.subList(1, MOVES).max()) }}")
                appendLine("Dropping it (ms): ${drags.map { "%.1f".format(it.last()) }}")
                appendLine("Frames while dragging: ${durations.size}")
                for (p in listOf(50, 95, 99)) appendLine("  p$p: ${"%.1f".format(percentile(durations, p))} ms")
                appendLine("  max: ${"%.1f".format(durations.lastOrNull() ?: 0.0)} ms")
            }
        Log.i(TAG, report)
        TestStorage().openOutputFile("selection-report.txt").use { it.write(report.toByteArray()) }
        scenario.close()
        // One edit per drag; the stamps leave copies, the selection stays the original.
        assertEquals(report, DRAGS, edits.size)
        assertEquals(report, selected, stamped)
        assertTrue(report, percentile(durations, 95) < FRAME_BUDGET_MS)
    }

    /** The 104-page note with the rectangle selection tool, reporting its edits to [edits]. */
    private fun open(edits: MutableList<Edit>): Pair<ActivityScenario<ComponentActivity>, DocumentView> {
        val sample =
            instrumentation.context.assets
                .open("physics.xopp")
                .source()
                .buffer()
                .use { XoppReader.read(it.readByteString()).document }
        lateinit var view: DocumentView
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            view = DocumentView(activity)
            view.setDocument(
                List(REPEATS) { sample.pages }.flatten(),
                RenderCaches.forDevice(activity),
                { PageRenderer(activity.applicationContext) },
            )
            view.tools = view.tools.copy(tool = Tool.SELECT, selectMode = SelectMode.RECTANGLE)
            view.onEdit = { edits += it }
            activity.setContentView(view)
        }
        SystemClock.sleep(SETTLE_MS)
        return scenario to view
    }

    private fun bounds(view: DocumentView): RectF = onMain { checkNotNull(view.selection.bounds) }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** [MOVES] steps from [from] to [to]. */
    private fun line(
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
    ) = (0..MOVES).map {
        from.first + (to.first - from.first) * it / MOVES to from.second + (to.second - from.second) * it / MOVES
    }

    private fun percentile(
        sorted: List<Double>,
        p: Int,
    ): Double = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * p / 100.0).roundToInt()]

    private companion object {
        const val TAG = "SelectionDeviceTest"
        const val REPEATS = 13
        const val MOVES = 120
        const val DRAGS = 6
        const val MARGIN = 0.05f
        const val BOTTOM = 0.6f
        const val STEP = 0.08f
        val STAMP_FIRST = 40..45
        val STAMP_SECOND = 80..85
        const val PNG_QUALITY = 100
        const val SETTLE_MS = 2000L
        const val NANOS_PER_MS = 1_000_000.0
        const val FRAME_BUDGET_MS = 16.7
    }
}
