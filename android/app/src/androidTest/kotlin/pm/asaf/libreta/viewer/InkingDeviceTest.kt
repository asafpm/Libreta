// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.content.pm.ApplicationInfo
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.libreta.android.render.PageRenderer
import pm.asaf.libreta.android.render.RenderCaches
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.NewNote
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageChange
import pm.asaf.libreta.core.model.PaperSize
import pm.asaf.libreta.core.model.PenSettings
import pm.asaf.libreta.core.model.PenSize
import java.util.Collections
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Writing on the device with injected stylus strokes and the real front buffer: frames stay
 * fast while writing on the 104-page note, a stroke is on screen in every screenshot after it
 * is lifted (no flicker as the page takes it over), and its wet ink doesn't linger when the
 * page moves. Writes inking-report.txt (TestStorage).
 */
@RunWith(AndroidJUnit4::class)
class InkingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** A note view with wet ink, as on the note screen. */
    private class Note(
        val scenario: ActivityScenario<ComponentActivity>,
        val view: DocumentView,
        val edits: MutableList<Int>,
    )

    private fun open(pages: List<Page>): Note {
        val edits = Collections.synchronizedList(ArrayList<Int>())
        lateinit var view: DocumentView
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            view = DocumentView(activity)
            view.setDocument(pages, RenderCaches.forDevice(activity), { PageRenderer(activity.applicationContext) })
            view.pen = PenSettings(RED, PenSize.THICK)
            view.onEdit = { edit -> edits += (edit.changes.single() as PageChange).index }
            val ink = LowLatencyCanvasView(activity)
            view.wetInk = LowLatencyWetInk(ink)
            activity.setContentView(
                FrameLayout(activity).apply {
                    addView(view)
                    addView(ink)
                },
            )
        }
        SystemClock.sleep(SETTLE_MS)
        return Note(scenario, view, edits)
    }

    private fun physics(): List<Page> {
        val sample =
            instrumentation.context.assets
                .open("physics.xopp")
                .source()
                .buffer()
                .use { XoppReader.read(it.readByteString()).document }
        return List(REPEATS) { sample.pages }.flatten()
    }

    @Test
    fun strokesAreSmoothAndNeverFlicker() {
        val note = open(physics())
        val view = note.view
        // Frame times, undisturbed: strokes down the page.
        val frames = Collections.synchronizedList(ArrayList<Long>())
        val metricsThread = HandlerThread("frame-metrics").apply { start() }
        val listener =
            Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                frames +=
                    metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            }
        note.scenario.onActivity {
            it.window.addOnFrameMetricsAvailableListener(
                listener,
                Handler(metricsThread.looper),
            )
        }
        repeat(STROKES) { stroke -> write(view, 0.2f * view.width, rowY(view, stroke, STROKES), 0.6f * view.width) }
        SystemClock.sleep(SETTLE_MS)
        note.scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
        metricsThread.quitSafely()

        // Flicker: screenshots right after lifting, between the first strokes' rows.
        val misses = ArrayList<String>()
        repeat(CHECKED_STROKES) { stroke ->
            val y = rowY(view, stroke, CHECKED_STROKES) + ROW_OFFSET
            write(view, 0.2f * view.width, y, 0.6f * view.width)
            val lifted = SystemClock.uptimeMillis()
            repeat(SHOTS) { shot ->
                if (!redOnScreen(view, y)) {
                    misses +=
                        "$stroke/$shot (${SystemClock.uptimeMillis() - lifted} ms after lifting)"
                }
            }
        }

        val durations = synchronized(frames) { frames.map { it / NANOS_PER_MS } }.sorted()
        val report =
            buildString {
                val build = if (debuggable) "debuggable" else "release-like"
                appendLine("Strokes: $STROKES + $CHECKED_STROKES of $MOVES moves ($build build)")
                appendLine("Committed: ${note.edits.size}")
                appendLine("Stroke missing after lifting: ${misses.size} of ${CHECKED_STROKES * SHOTS} screenshots")
                misses.forEach { appendLine("  $it") }
                appendLine("Frames while writing: ${durations.size}")
                for (p in listOf(50, 95, 99)) appendLine("  p$p: ${"%.1f".format(percentile(durations, p))} ms")
                appendLine("  max: ${"%.1f".format(durations.lastOrNull() ?: 0.0)} ms")
            }
        Log.i(TAG, report)
        TestStorage().openOutputFile("inking-report.txt").use { it.write(report.toByteArray()) }
        note.scenario.close()
        assertEquals(report, STROKES + CHECKED_STROKES, note.edits.size)
        assertEquals(report, 0, misses.size)
        assertTrue(report, percentile(durations, 95) < FRAME_BUDGET_MS)
    }

    @Test
    fun wetInkDoesNotLingerWhenThePageMoves() {
        val page = NewNote.document(PaperSize.A4, landscape = false, creator = "test").pages
        val note = open(page + page)
        val view = note.view
        val y = view.height * 0.5f
        write(view, 0.2f * view.width, y, 0.6f * view.width)
        SystemClock.sleep(SETTLE_MS)
        assertTrue(redOnScreen(view, y))
        // A finger scrolls the page up; the stroke goes with it.
        drag(view, 0.5f * view.height, 0.5f * view.height - SCROLL)
        SystemClock.sleep(SETTLE_MS)
        assertFalse("wet ink left behind", redOnScreen(view, y))
        assertTrue(redOnScreen(view, y - SCROLL))
        note.scenario.close()
    }

    /** Whether the pen's red is near view row [y], mid-width, in a screenshot (the stroke waves). */
    private fun redOnScreen(
        view: DocumentView,
        y: Float,
    ): Boolean = TestInput(instrumentation, view).colorOnScreen(RED, view.width / 2f, y)

    /** The height of stroke [index] of [count], down the view. */
    private fun rowY(
        view: DocumentView,
        index: Int,
        count: Int,
    ) = view.height * (TOP + SPAN * index / count)

    /** A wavy stroke from ([x], [y]), [length] pixels to the right, at about 240 events per second. */
    private fun write(
        view: DocumentView,
        x: Float,
        y: Float,
        length: Float,
    ) = TestInput(instrumentation, view).touch(
        (0..MOVES).map {
            x + length * it / MOVES to
                y + WAVE * sin(it * WAVE_STEP)
        },
    )

    /** A slow finger drag from view row [fromY] to [toY], mid-width. */
    private fun drag(
        view: DocumentView,
        fromY: Float,
        toY: Float,
    ) {
        val x = view.width / 2f
        val points = (0..MOVES).map { x to fromY + (toY - fromY) * it / MOVES }
        // Held still at the end, so that it doesn't fling.
        TestInput(instrumentation, view).touch(
            points,
            MotionEvent.TOOL_TYPE_FINGER,
            TestInput.EVENT_MS * 2,
            SETTLE_MS / 4,
        )
    }

    private fun percentile(
        sorted: List<Double>,
        p: Int,
    ): Double = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * p / 100.0).roundToInt()]

    private companion object {
        const val TAG = "InkingDeviceTest"
        const val REPEATS = 13
        const val STROKES = 30
        const val CHECKED_STROKES = 30
        const val SHOTS = 3
        const val MOVES = 60
        const val SETTLE_MS = 2000L
        const val WAVE = 6f
        const val WAVE_STEP = 0.3f
        const val TOP = 0.15f
        const val SPAN = 0.7f
        const val ROW_OFFSET = 25f
        const val SCROLL = 300f
        const val NANOS_PER_MS = 1_000_000.0
        const val FRAME_BUDGET_MS = 16.7
        val RED = Color.of(237, 83, 83)
    }
}
