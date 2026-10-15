// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.libreta.android.render.PageRenderer
import pm.asaf.libreta.android.render.RenderCaches
import pm.asaf.libreta.android.render.RenderThreads
import pm.asaf.libreta.android.render.Thumbnails
import pm.asaf.libreta.android.render.TileCache
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.geometry.DocumentLayout
import pm.asaf.libreta.core.geometry.Size
import pm.asaf.libreta.core.geometry.Viewport
import pm.asaf.libreta.core.geometry.ViewportNavigator
import pm.asaf.libreta.core.model.Page
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentViewTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val pages: List<Page> =
        File(checkNotNull(System.getProperty("libreta.testdata")), "physics.xopp")
            .source()
            .buffer()
            .use { XoppReader.read(it.readByteString()).document.pages }
    private val layout = DocumentLayout(pages.map { Size(it.width, it.height) })
    private val navigator = ViewportNavigator(layout)
    private val scheduler = TestCoroutineScheduler()
    private val caches = RenderCaches(TileCache(Long.MAX_VALUE), Thumbnails(Long.MAX_VALUE))
    private lateinit var view: DocumentView

    private val fitWidth get() = navigator.fitWidthZoom(WIDTH.toDouble())
    private val viewport: Viewport get() = checkNotNull(view.viewport)

    @Before
    fun setUp() {
        view = DocumentView(context)
        val dispatcher = StandardTestDispatcher(scheduler)
        view.setDocument(pages, caches, { PageRenderer(context) }, RenderThreads(workers = 2, dispatcher, dispatcher))
        layOut(WIDTH, HEIGHT)
    }

    @After
    fun tearDown() = view.close()

    private fun layOut(
        width: Int,
        height: Int,
    ) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    // Touch input

    private var time = 1_000L
    private var downTime = 0L

    private fun send(
        action: Int,
        vararg points: Pair<Float, Float>,
    ) {
        if (action == MotionEvent.ACTION_DOWN) downTime = time
        val properties =
            Array(points.size) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val coordinates =
            Array(points.size) { index ->
                MotionEvent.PointerCoords().apply {
                    x = points[index].first
                    y = points[index].second
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
        view.onTouchEvent(event)
        event.recycle()
    }

    /** One finger from [from] to [to], slowly enough not to fling. */
    private fun drag(
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
    ) {
        send(MotionEvent.ACTION_DOWN, from)
        for (step in 1..STEPS) {
            time += 100
            val t = step.toFloat() / STEPS
            send(
                MotionEvent.ACTION_MOVE,
                from.first + (to.first - from.first) * t to from.second + (to.second - from.second) * t,
            )
        }
        time += 500
        send(MotionEvent.ACTION_UP, to)
    }

    private fun pointerAction(
        action: Int,
        index: Int,
    ) = action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    /** Two fingers around ([x], [y]), moving apart from [startSpan] to [endSpan]; lifted if [lift]. */
    private fun pinch(
        x: Float,
        y: Float,
        startSpan: Float,
        endSpan: Float,
        lift: Boolean = true,
    ) {
        fun fingers(span: Float) = arrayOf(x - span / 2 to y, x + span / 2 to y)
        send(MotionEvent.ACTION_DOWN, fingers(startSpan)[0])
        time += 10
        send(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), *fingers(startSpan))
        for (step in 1..STEPS) {
            time += 20
            send(MotionEvent.ACTION_MOVE, *fingers(startSpan + (endSpan - startSpan) * step / STEPS))
        }
        if (lift) {
            time += 20
            send(pointerAction(MotionEvent.ACTION_POINTER_UP, 1), *fingers(endSpan))
            time += 20
            send(MotionEvent.ACTION_UP, fingers(endSpan)[0])
        }
    }

    private fun tap(
        x: Float,
        y: Float,
    ) {
        send(MotionEvent.ACTION_DOWN, x to y)
        time += 50
        send(MotionEvent.ACTION_UP, x to y)
    }

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-6,
    ) = assertTrue("expected $expected, got $actual", abs(expected - actual) <= tolerance)

    // Tests

    @Test
    fun startsAtFitWidthAtTheTop() {
        assertEquals(fitWidth, viewport.zoom, 1e-9)
        assertEquals(0.0, viewport.offsetY, 0.0)
    }

    @Test
    fun panningScrollsAndStopsAtTheEdges() {
        drag(500f to 1200f, 500f to 400f)
        assertClose(800 / fitWidth, viewport.offsetY, 1.0)
        drag(500f to 200f, 500f to 1300f)
        assertEquals(0.0, viewport.offsetY, 0.0)
        // At fit width there is nothing to pan sideways.
        drag(200f to 500f, 900f to 500f)
        assertEquals(0.0, viewport.offsetX, 0.0)
    }

    @Test
    fun pinchZoomsAroundTheFingers() {
        val focus = viewport.viewToLayout(500.0, 700.0)
        pinch(500f, 700f, startSpan = 300f, endSpan = 700f)
        assertTrue("zoom ${viewport.zoom}", viewport.zoom > 1.8 * fitWidth)
        val after = viewport.viewToLayout(500.0, 700.0)
        assertClose(focus.first, after.first, 1e-3)
        assertClose(focus.second, after.second, 1e-3)
    }

    @Test
    fun doubleTapTogglesZoom() {
        tap(500f, 300f)
        time += 100
        tap(500f, 300f)
        assertClose(DocumentView.DOUBLE_TAP_ZOOM * fitWidth, viewport.zoom)
        time += 1000
        tap(500f, 300f)
        time += 100
        tap(500f, 300f)
        assertClose(fitWidth, viewport.zoom)
    }

    @Test
    fun tilesAreRenderedOnceThePinchEnds() {
        scheduler.advanceUntilIdle()
        val atFitWidth = caches.tiles.size
        assertTrue(atFitWidth > 0)
        pinch(500f, 700f, startSpan = 300f, endSpan = 700f, lift = false)
        scheduler.advanceUntilIdle()
        // Mid-pinch only thumbnails are rendered.
        assertEquals(atFitWidth, caches.tiles.size)
        time += 20
        send(pointerAction(MotionEvent.ACTION_POINTER_UP, 1), 150f to 700f, 850f to 700f)
        send(MotionEvent.ACTION_UP, 150f to 700f)
        scheduler.advanceUntilIdle()
        var atNewZoom = 0
        caches.tiles.forEachOf(0) { key, _ -> if (key.zoom == viewport.zoom.toFloat()) atNewZoom++ }
        assertTrue("$atNewZoom tiles at the new zoom", atNewZoom > 0)
    }

    @Test
    fun drawsThePageAsRendered() {
        scheduler.advanceUntilIdle()
        val shown = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(shown))
        val zoom = viewport.zoom.toFloat()
        val page = PageRenderer(context).renderToBitmap(pages[0], zoom)
        val bounds = layout.pageBounds(0)
        val left = ((bounds.left - viewport.offsetX) * viewport.zoom).roundToInt()
        val top = ((bounds.top - viewport.offsetY) * viewport.zoom).roundToInt()
        var differing = 0
        val checked = 600
        for (y in 0 until checked) {
            for (x in 0 until checked) {
                val a = shown.getPixel(left + 100 + x, top + 100 + y)
                val b = page.getPixel(100 + x, 100 + y)
                if ((0 until 32 step 8).any { abs((a ushr it and 0xff) - (b ushr it and 0xff)) > 2 }) differing++
            }
        }
        // Tiles differ from a whole-page render in rare anti-aliased pixels (see TileRenderingTest);
        // a 1 px misplacement makes about 7% of these pixels differ.
        assertTrue("$differing pixels differ", differing < checked * checked / 200)
    }

    @Test
    fun keepsItsPlaceWhenResized() {
        drag(500f to 1300f, 500f to 100f)
        drag(500f to 1300f, 500f to 100f)
        val before = navigator.anchorOf(viewport)
        layOut(HEIGHT, WIDTH)
        val after = navigator.anchorOf(viewport)
        assertEquals(before.page, after.page)
        assertClose(before.pageY, after.pageY, 1e-6)
        assertClose(before.relativeZoom, after.relativeZoom, 1e-9)
    }

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 1400
        const val STEPS = 8
    }
}
