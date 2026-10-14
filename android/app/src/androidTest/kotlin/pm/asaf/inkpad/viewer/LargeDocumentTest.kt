// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import okio.buffer
import okio.source
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.android.render.RenderCaches
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.core.format.XoppWriter
import pm.asaf.inkpad.core.geometry.DocumentLayout
import pm.asaf.inkpad.core.geometry.Size
import pm.asaf.inkpad.core.model.Document
import java.util.Collections
import kotlin.math.roundToInt

/**
 * Phase 3's goal on the device: a ~100-page note opens quickly, scrolls and zooms smoothly,
 * and the caches stay within their budgets. Writes large-document-report.txt (TestStorage).
 *
 * Load time is only checked on non-debuggable builds (debuggable ones run several times
 * slower):
 *   ./gradlew :android:app:connectedBenchmarkAndroidTest -Pinkpad.testBuildType=benchmark
 */
@RunWith(AndroidJUnit4::class)
class LargeDocumentTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** physics.xopp's 8 pages, 13 times. */
    private fun largeDocument(): Document {
        val sample =
            instrumentation.context.assets
                .open("physics.xopp")
                .source()
                .buffer()
                .use { XoppReader.read(it.readByteString()).document }
        return sample.copy(pages = List(REPEATS) { sample.pages }.flatten())
    }

    /** Frame times and peak cache sizes while the view is used. */
    private class Recorder(
        private val caches: RenderCaches,
    ) : Window.OnFrameMetricsAvailableListener {
        private val frames = Collections.synchronizedList(ArrayList<Long>())

        @Volatile var peakTiles = 0L

        @Volatile var peakThumbnails = 0L

        override fun onFrameMetricsAvailable(
            window: Window,
            metrics: FrameMetrics,
            dropCount: Int,
        ) {
            frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            peakTiles = maxOf(peakTiles, caches.tiles.sizeBytes)
            peakThumbnails = maxOf(peakThumbnails, caches.thumbnails.sizeBytes)
        }

        /** Frame times in milliseconds, shortest first. */
        fun durations(): List<Double> = synchronized(frames) { frames.map { it / NANOS_PER_MS } }.sorted()
    }

    /** How scrolling to the end went. */
    private class Scroll(
        val flings: Int,
        val milliseconds: Long,
        val reachedEnd: Boolean,
    )

    @Test
    fun largeDocumentIsSmooth() {
        val pages = largeDocument().pages
        val bytes = XoppWriter.toByteString(largeDocument())
        val loadStart = SystemClock.elapsedRealtime()
        val document = XoppReader.read(bytes).document
        val loadMs = SystemClock.elapsedRealtime() - loadStart
        val height = DocumentLayout(pages.map { Size(it.width, it.height) }).height

        val caches = RenderCaches.forDevice(context)
        val recorder = Recorder(caches)
        val metricsThread = HandlerThread("frame-metrics").apply { start() }
        lateinit var view: DocumentView
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                view = DocumentView(activity)
                view.setDocument(document.pages, caches, { PageRenderer(activity.applicationContext) })
                activity.setContentView(view)
            }
            settle()
            scenario.onActivity {
                it.window.addOnFrameMetricsAvailableListener(
                    recorder,
                    Handler(metricsThread.looper),
                )
            }
            val scroll = scrollToEnd(view, height)
            pinch(view, from = 400f, to = 1200f)
            settle()
            pinch(view, from = 1200f, to = 400f)
            settle()
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(recorder) }

            val durations = recorder.durations()
            val report = report(document.pages.size, bytes.size, loadMs, scroll, durations, recorder, caches)
            Log.i(TAG, report)
            TestStorage().openOutputFile("large-document-report.txt").use { it.write(report.toByteArray()) }

            assertTrue(report, scroll.reachedEnd)
            if (!debuggable) assertTrue(report, loadMs < MAX_LOAD_MS)
            assertTrue(report, percentile(durations, 95) < FRAME_BUDGET_MS)
            assertTrue(report, (durations.lastOrNull() ?: 0.0) < MAX_FRAME_MS)
            assertTrue(
                report,
                recorder.peakTiles <= caches.tiles.maxBytes && recorder.peakThumbnails <= caches.thumbnails.maxBytes,
            )
        }
        metricsThread.quitSafely()
        view.post { view.close() }
    }

    /** Flings up until the end of the document, [height] layout units, is in view. */
    private fun scrollToEnd(
        view: DocumentView,
        height: Double,
    ): Scroll {
        val start = SystemClock.elapsedRealtime()
        var flings = 0
        while (!atBottom(view, height) && SystemClock.elapsedRealtime() - start < SCROLL_LIMIT_MS) {
            swipeUp(view)
            flings++
            waitWhileMoving(view)
        }
        return Scroll(flings, SystemClock.elapsedRealtime() - start, atBottom(view, height))
    }

    @Suppress("LongParameterList") // one line of the report each
    private fun report(
        pages: Int,
        bytes: Int,
        loadMs: Long,
        scroll: Scroll,
        durations: List<Double>,
        recorder: Recorder,
        caches: RenderCaches,
    ) = buildString {
        appendLine("Pages: $pages, file: ${bytes / KB} KB")
        appendLine("Load (parse): $loadMs ms${if (debuggable) " (debuggable build: not checked)" else ""}")
        appendLine("Scroll to the end: ${scroll.flings} flings, ${scroll.milliseconds} ms")
        appendLine("  reached the end: ${scroll.reachedEnd}")
        appendLine("Frames: ${durations.size}")
        for (p in listOf(50, 90, 95, 99)) appendLine("  p$p: ${"%.1f".format(percentile(durations, p))} ms")
        appendLine("  max: ${"%.1f".format(durations.lastOrNull() ?: 0.0)} ms")
        val slow = durations.count { it > FRAME_BUDGET_MS }
        val verySlow = durations.count { it > MAX_FRAME_MS }
        appendLine("  over 16.7 ms: $slow, over 100 ms: $verySlow")
        appendLine("Tiles: peak ${recorder.peakTiles / MB} MB of ${caches.tiles.maxBytes / MB} MB")
        appendLine("Thumbnails: peak ${recorder.peakThumbnails / MB} MB of ${caches.thumbnails.maxBytes / MB} MB")
    }

    private fun percentile(
        sorted: List<Double>,
        p: Int,
    ): Double = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * p / 100.0).roundToInt()]

    /** Whether the end of the document, [height] layout units, is in view. */
    private fun atBottom(
        view: DocumentView,
        height: Double,
    ): Boolean {
        var bottom = false
        instrumentation.runOnMainSync { bottom = (view.viewport?.visible?.bottom ?: 0.0) >= height - 1 }
        return bottom
    }

    private fun offset(view: DocumentView): Double {
        var offset = 0.0
        instrumentation.runOnMainSync { offset = view.viewport?.offsetY ?: 0.0 }
        return offset
    }

    private fun waitWhileMoving(view: DocumentView) {
        var last = offset(view)
        while (true) {
            SystemClock.sleep(STILL_MS)
            val now = offset(view)
            if (now == last) return
            last = now
        }
    }

    private fun settle() = SystemClock.sleep(SETTLE_MS)

    /** A quick upward swipe, which flings. */
    private fun swipeUp(view: DocumentView) {
        val x = view.width / 2f
        val fromY = view.height * 0.8f
        val toY = view.height * 0.2f
        val down = SystemClock.uptimeMillis()
        touch(view, down, down, MotionEvent.ACTION_DOWN, x to fromY)
        for (step in 1..SWIPE_STEPS) {
            val time = down + step * SWIPE_STEP_MS
            touch(view, down, time, MotionEvent.ACTION_MOVE, x to fromY + (toY - fromY) * step / SWIPE_STEPS)
        }
        touch(view, down, down + (SWIPE_STEPS + 1) * SWIPE_STEP_MS, MotionEvent.ACTION_UP, x to toY)
    }

    /** Two fingers moving from [from] to [to] pixels apart around the centre, one frame at a time. */
    private fun pinch(
        view: DocumentView,
        from: Float,
        to: Float,
    ) {
        val x = view.width / 2f
        val y = view.height / 2f

        fun fingers(span: Float) = arrayOf(x - span / 2 to y, x + span / 2 to y)
        val down = SystemClock.uptimeMillis()
        touch(view, down, down, MotionEvent.ACTION_DOWN, fingers(from)[0])
        touch(
            view,
            down,
            down,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            *fingers(from),
        )
        for (step in 1..PINCH_STEPS) {
            SystemClock.sleep(FRAME_MS)
            touch(
                view,
                down,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_MOVE,
                *fingers(from + (to - from) * step / PINCH_STEPS),
            )
        }
        val up = SystemClock.uptimeMillis()
        touch(
            view,
            down,
            up,
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            *fingers(to),
        )
        touch(view, down, up, MotionEvent.ACTION_UP, fingers(to)[0])
    }

    private fun touch(
        view: DocumentView,
        downTime: Long,
        time: Long,
        action: Int,
        vararg points: Pair<Float, Float>,
    ) {
        val properties =
            Array(points.size) {
                MotionEvent.PointerProperties().apply {
                    id = it
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val coordinates =
            Array(points.size) {
                MotionEvent.PointerCoords().apply {
                    x = points[it].first
                    y = points[it].second
                    pressure = 1f
                    size = 1f
                }
            }
        val event =
            MotionEvent.obtain(
                downTime,
                time,
                action,
                points.size,
                properties,
                coordinates,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        instrumentation.runOnMainSync { view.dispatchTouchEvent(event) }
        event.recycle()
    }

    private companion object {
        const val TAG = "LargeDocumentTest"
        const val REPEATS = 13
        const val KB = 1024
        const val MB = 1024 * 1024
        const val NANOS_PER_MS = 1_000_000.0
        const val FRAME_BUDGET_MS = 16.7
        const val MAX_FRAME_MS = 100.0
        const val MAX_LOAD_MS = 3000
        const val SCROLL_LIMIT_MS = 120_000L
        const val SETTLE_MS = 2000L
        const val STILL_MS = 100L
        const val SWIPE_STEPS = 6
        const val SWIPE_STEP_MS = 8L
        const val PINCH_STEPS = 60
        const val FRAME_MS = 8L
    }
}
