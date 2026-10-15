// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.graphics.lowlatency.LowLatencyCanvasView
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
import pm.asaf.libreta.android.render.PageRenderer
import pm.asaf.libreta.android.render.RenderCaches
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.PenSettings
import pm.asaf.libreta.core.model.PenSize
import java.util.Collections
import kotlin.math.roundToInt

/**
 * Erasing on the device with the stylus's eraser end, on the 104-page note: frames stay fast
 * while the eraser goes over its busy pages, each gesture is one edit, and erased ink is gone
 * from the screen as soon as the eraser lifts, before the tiles are re-rendered. Writes
 * erasing-report.txt (TestStorage).
 */
@RunWith(AndroidJUnit4::class)
class ErasingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @Test
    fun erasingIsSmoothAndShowsAtOnce() {
        val edits = Collections.synchronizedList(ArrayList<Edit>())
        val (scenario, view) = open(edits)
        val input = TestInput(instrumentation, view)

        // Red lines across the page, settled into the tiles.
        val rows = (0 until LINES).map { view.height * (TOP + SPAN * it / LINES) }
        for (y in rows) input.touch((0..MOVES).map { 0.15f * view.width + 0.7f * view.width * it / MOVES to y })
        SystemClock.sleep(SETTLE_MS)
        val written = edits.size

        val frames = Collections.synchronizedList(ArrayList<Long>())
        val metricsThread = HandlerThread("frame-metrics").apply { start() }
        val listener =
            Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                frames +=
                    metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            }
        scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(metricsThread.looper)) }
        val misses = eraseLines(input, view, rows)
        val lineErasures = edits.size - written
        eraseZigzags(input, view)
        SystemClock.sleep(SETTLE_MS)
        scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
        metricsThread.quitSafely()

        val durations = synchronized(frames) { frames.map { it / NANOS_PER_MS } }.sorted()
        val erasures = edits.drop(written)
        val report =
            buildString {
                val build = if (debuggable) "debuggable" else "release-like"
                appendLine("Eraser gestures: $LINES across lines + $ZIGZAGS zigzags ($build build)")
                appendLine("Edits: $lineErasures for the lines, ${erasures.size - lineErasures} for the zigzags")
                appendLine("  pages per edit: ${erasures.map { it.changes.size }.distinct()}")
                appendLine("Erased line still on screen when the eraser lifted: ${misses.size} of $LINES")
                misses.forEach { appendLine("  $it") }
                appendLine("Frames while erasing: ${durations.size}")
                for (p in listOf(50, 95, 99)) appendLine("  p$p: ${"%.1f".format(percentile(durations, p))} ms")
                appendLine("  max: ${"%.1f".format(durations.lastOrNull() ?: 0.0)} ms")
            }
        Log.i(TAG, report)
        TestStorage().openOutputFile("erasing-report.txt").use { it.write(report.toByteArray()) }
        scenario.close()
        // One edit per gesture.
        assertEquals(report, LINES, lineErasures)
        assertTrue(report, erasures.size <= LINES + ZIGZAGS)
        assertEquals(report, 0, misses.size)
        assertTrue(report, percentile(durations, 95) < FRAME_BUDGET_MS)
    }

    /** The 104-page note in a view with wet ink, reporting its edits to [edits]. */
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
            view.pen = PenSettings(RED, PenSize.THICK)
            view.onEdit = { edits += it }
            val ink = LowLatencyCanvasView(activity)
            view.wetInk = LowLatencyWetInk(ink)
            activity.setContentView(FrameLayout(activity).apply { addView(view) }.apply { addView(ink) })
        }
        SystemClock.sleep(SETTLE_MS)
        return scenario to view
    }

    /** Down across each line in [rows]; returns the lines still seen when the eraser lifts. */
    private fun eraseLines(
        input: TestInput,
        view: DocumentView,
        rows: List<Float>,
    ): List<String> {
        val misses = ArrayList<String>()
        rows.forEachIndexed { index, y ->
            val x = view.width * (0.25f + 0.5f * index / LINES)
            if (!input.colorOnScreen(RED, x, y)) {
                TestStorage().openOutputFile("erasing-failure.png").use {
                    instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
                }
                error("line $index not drawn at $x, $y")
            }
            input.touch((0..MOVES).map { x to y - SWEEP + 2 * SWEEP * it / MOVES }, MotionEvent.TOOL_TYPE_ERASER)
            if (input.colorOnScreen(RED, x, y, search = 2)) misses += "line $index"
        }
        return misses
    }

    /** Zigzags over the page's own ink. */
    private fun eraseZigzags(
        input: TestInput,
        view: DocumentView,
    ) = repeat(ZIGZAGS) { zigzag ->
        val points =
            (0..MOVES * 4).map {
                val x = view.width * (0.1f + 0.8f * it / (MOVES * 4f))
                val tooth = it % TOOTH
                val y =
                    view.height * (0.2f + 0.1f * zigzag) +
                        TOOTH_STEP * if (it / TOOTH % 2 == 0) tooth else TOOTH - tooth
                x to y
            }
        input.touch(points, MotionEvent.TOOL_TYPE_ERASER)
    }

    private fun percentile(
        sorted: List<Double>,
        p: Int,
    ): Double = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * p / 100.0).roundToInt()]

    private companion object {
        const val TAG = "ErasingDeviceTest"
        const val REPEATS = 13
        const val LINES = 12
        const val ZIGZAGS = 4
        const val MOVES = 60
        const val SWEEP = 60f
        const val TOOTH = 20
        const val TOOTH_STEP = 8f
        const val PNG_QUALITY = 100
        const val SETTLE_MS = 2000L
        const val TOP = 0.15f
        const val SPAN = 0.6f
        const val NANOS_PER_MS = 1_000_000.0
        const val FRAME_BUDGET_MS = 16.7
        val RED = Color.of(237, 83, 83)
    }
}
