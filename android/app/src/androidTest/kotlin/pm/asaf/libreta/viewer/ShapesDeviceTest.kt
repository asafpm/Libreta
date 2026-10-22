// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
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
import pm.asaf.libreta.android.render.PageRenderer
import pm.asaf.libreta.android.render.RenderCaches
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.DrawingType
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.Tool
import java.util.Collections
import kotlin.math.roundToInt

/**
 * Drawing shapes on a busy page of the 104-page note: frames stay fast while each shape follows
 * the pen (it is drawn again whole on every move), and each shape is one edit. Writes
 * shapes-report.txt (TestStorage).
 */
@RunWith(AndroidJUnit4::class)
class ShapesDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @Test
    fun shapesFollowThePenSmoothly() {
        val edits = Collections.synchronizedList(ArrayList<Edit>())
        val (scenario, view) = open(edits)
        val input = TestInput(instrumentation, view)

        val frames = Collections.synchronizedList(ArrayList<Long>())
        val metricsThread = HandlerThread("frame-metrics").apply { start() }
        val listener =
            Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            }
        scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(metricsThread.looper)) }
        val shapes = listOf(DrawingType.ELLIPSE, DrawingType.ARROW, DrawingType.RECTANGLE, DrawingType.ELLIPSE)
        shapes.forEachIndexed { i, type ->
            val tool = if (i % 2 == 0) Tool.PEN else Tool.HIGHLIGHTER
            instrumentation.runOnMainSync { view.tools = view.tools.copy(tool = tool, drawingType = type) }
            val top = view.height * (0.15f + 0.18f * i)
            input.touch(line(view.width * 0.15f to top, view.width * 0.85f to top + view.height * 0.12f))
        }
        SystemClock.sleep(SETTLE_MS)
        scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
        metricsThread.quitSafely()

        val durations = synchronized(frames) { frames.map { it / NANOS_PER_MS } }.sorted()
        val report =
            buildString {
                val build = if (debuggable) "debuggable" else "release-like"
                appendLine("Shapes drawn: ${shapes.size} ($build build)")
                appendLine("Edits: ${edits.size}")
                appendLine("Frames while drawing: ${durations.size}")
                for (p in listOf(50, 95, 99)) appendLine("  p$p: ${"%.1f".format(percentile(durations, p))} ms")
                appendLine("  max: ${"%.1f".format(durations.lastOrNull() ?: 0.0)} ms")
            }
        Log.i(TAG, report)
        TestStorage().openOutputFile("shapes-report.txt").use { it.write(report.toByteArray()) }
        scenario.close()
        assertEquals(report, shapes.size, edits.size)
        assertTrue(report, percentile(durations, 95) < FRAME_BUDGET_MS)
    }

    /** The 104-page note with the pen, reporting its edits to [edits]. */
    private fun open(edits: MutableList<Edit>): Pair<ActivityScenario<ComponentActivity>, DocumentView> {
        val sample =
            instrumentation.context.assets
                .open("libreta-sample.xopp")
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
            view.tools = view.tools.copy(tool = Tool.PEN)
            view.onEdit = { edits += it }
            activity.setContentView(view)
        }
        SystemClock.sleep(SETTLE_MS)
        return scenario to view
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
        const val TAG = "ShapesDeviceTest"
        const val REPEATS = 13
        const val MOVES = 120
        const val SETTLE_MS = 2000L
        const val NANOS_PER_MS = 1_000_000.0
        const val FRAME_BUDGET_MS = 16.7
    }
}
