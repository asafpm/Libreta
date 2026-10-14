// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.android.render.RenderCaches
import pm.asaf.inkpad.android.render.RenderThreads
import pm.asaf.inkpad.android.render.Thumbnails
import pm.asaf.inkpad.android.render.TileCache
import pm.asaf.inkpad.core.geometry.DocumentLayout
import pm.asaf.inkpad.core.geometry.Size
import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.Layer
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.model.PageStyle
import pm.asaf.inkpad.core.model.PenSettings
import pm.asaf.inkpad.core.model.PenSize
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkingTest {
    /** Records what would be drawn as wet ink. */
    private class RecordingWetInk : WetInk {
        val segments = ArrayList<WetSegment>()
        var commits = 0
        var cancels = 0
        var clears = 0

        override fun update(
            color: Int,
            segments: List<WetSegment>,
            prediction: List<WetSegment>,
        ) {
            this.segments += segments
        }

        override fun commit() {
            commits++
        }

        override fun cancel() {
            cancels++
        }

        override fun clear() {
            clears++
        }
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // Two lined A4 pages, the second with two layers.
    private val blank =
        Page(595.28, 841.89, Background.Solid(Color.of(255, 255, 255), PageStyle.LINED), listOf(Layer(emptyList())))
    private val pages = listOf(blank, blank.copy(layers = listOf(Layer(emptyList()), Layer(emptyList(), name = "top"))))
    private val layout = DocumentLayout(pages.map { Size(it.width, it.height) })
    private val scheduler = TestCoroutineScheduler()
    private val caches = RenderCaches(TileCache(Long.MAX_VALUE), Thumbnails(Long.MAX_VALUE))
    private val wetInk = RecordingWetInk()
    private val edits = ArrayList<Pair<Int, Page>>()
    private lateinit var view: DocumentView

    @Before
    fun setUp() {
        view = DocumentView(context)
        val dispatcher = StandardTestDispatcher(scheduler)
        view.setDocument(pages, caches, { PageRenderer(context) }, RenderThreads(workers = 1, dispatcher, dispatcher))
        view.wetInk = wetInk
        view.pen = PenSettings(Color.of(255, 0, 0), PenSize.THICK)
        view.onEdit = { edit -> edit.changes.forEach { edits += it.index to it.after } }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, WIDTH, HEIGHT)
    }

    @After
    fun tearDown() = view.close()

    // Input

    private var time = 1_000L
    private var downTime = 0L

    private class Pointer(
        val id: Int,
        val tool: Int,
        val x: Float,
        val y: Float,
        val pressure: Float = 0.5f,
    )

    private fun send(
        action: Int,
        vararg pointers: Pointer,
        flags: Int = 0,
        buttons: Int = 0,
    ) {
        if (action == MotionEvent.ACTION_DOWN) downTime = time
        val properties =
            pointers.map {
                MotionEvent.PointerProperties().apply {
                    id = it.id
                    toolType = it.tool
                }
            }
        val coordinates =
            pointers.map {
                MotionEvent.PointerCoords().apply {
                    x = it.x
                    y = it.y
                    pressure = it.pressure
                    size = 1f
                }
            }
        val event =
            MotionEvent.obtain(
                downTime,
                time,
                action,
                pointers.size,
                properties.toTypedArray(),
                coordinates.toTypedArray(),
                0,
                buttons,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN,
                flags,
            )
        view.dispatchTouchEvent(event)
        event.recycle()
        time += 10
    }

    private fun stylus(
        x: Float,
        y: Float,
        pressure: Float = 0.5f,
    ) = Pointer(0, MotionEvent.TOOL_TYPE_STYLUS, x, y, pressure)

    private fun finger(
        x: Float,
        y: Float,
        id: Int = 0,
    ) = Pointer(id, MotionEvent.TOOL_TYPE_FINGER, x, y)

    /** A stylus line from (x, y) going right by [length] pixels. */
    private fun write(
        x: Float,
        y: Float,
        length: Float = 200f,
    ) {
        send(MotionEvent.ACTION_DOWN, stylus(x, y))
        for (step in 1..STEPS) send(MotionEvent.ACTION_MOVE, stylus(x + length * step / STEPS, y))
        send(MotionEvent.ACTION_UP, stylus(x + length, y))
    }

    private fun drawFrame() = view.draw(Canvas(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)))

    private val viewport get() = checkNotNull(view.viewport)

    // Tests

    @Test
    fun theStylusWritesOnThePageUnderIt() {
        write(300f, 400f)
        val (index, page) = edits.single()
        assertEquals(0, index)
        val stroke =
            page.layers
                .single()
                .elements
                .single() as Element.Stroke
        assertEquals(Color.of(255, 0, 0), stroke.color)
        assertEquals(PenSize.THICK.width, stroke.width, 0.0)
        // Page coordinates: the view point minus the page's position, divided by the zoom.
        val pageLeft = Math.round((layout.pageBounds(0).left - viewport.offsetX) * viewport.zoom).toDouble()
        val pageTop = Math.round((layout.pageBounds(0).top - viewport.offsetY) * viewport.zoom).toDouble()
        assertClose((300 - pageLeft) / viewport.zoom, stroke.points.x(0))
        assertClose((400 - pageTop) / viewport.zoom, stroke.points.y(0))
        assertClose((500 - pageLeft) / viewport.zoom, stroke.points.x(stroke.points.size - 1))
        // Pressure 0.5 everywhere: half the nominal width.
        assertTrue(checkNotNull(stroke.segmentWidths).toDoubleArray().all { abs(it - PenSize.THICK.width / 2) < 1e-6 })
        // Wet ink was drawn, committed on lifting, and goes once the page has drawn the stroke.
        assertTrue(wetInk.segments.isNotEmpty())
        assertEquals(1, wetInk.commits)
        assertEquals(0, wetInk.clears)
        repeat(3) { drawFrame() }
        assertEquals(0, wetInk.clears)
        drawFrame()
        assertEquals(1, wetInk.clears)
    }

    @Test
    fun strokesGoOnTheTopLayer() {
        // Scroll page 2 into view with a finger, then write 50 pt below its top.
        send(MotionEvent.ACTION_DOWN, finger(500f, 1300f))
        for (step in 1..STEPS) send(MotionEvent.ACTION_MOVE, finger(500f, 1300f - 1000f * step / STEPS))
        time += 500
        send(MotionEvent.ACTION_UP, finger(500f, 300f))
        val y = ((layout.pageBounds(1).top + 50 - viewport.offsetY) * viewport.zoom).toFloat()
        write(300f, y)
        val (index, page) = edits.single()
        assertEquals(1, index)
        assertEquals(listOf(0, 1), page.layers.map { it.elements.size })
    }

    @Test
    fun fingersPanUnlessTheyDraw() {
        val before = viewport.offsetY
        send(MotionEvent.ACTION_DOWN, finger(500f, 1200f))
        for (step in 1..STEPS) send(MotionEvent.ACTION_MOVE, finger(500f, 1200f - 400f * step / STEPS))
        send(MotionEvent.ACTION_UP, finger(500f, 800f))
        assertTrue(viewport.offsetY > before)
        assertTrue(edits.isEmpty())

        view.fingerDraws = true
        send(MotionEvent.ACTION_DOWN, finger(300f, 400f))
        for (step in 1..STEPS) send(MotionEvent.ACTION_MOVE, finger(300f + 200f * step / STEPS, 400f))
        send(MotionEvent.ACTION_UP, finger(500f, 400f))
        val stroke =
            edits
                .single()
                .second.layers
                .single()
                .elements
                .single() as Element.Stroke
        // Fingers have no pressure: constant width.
        assertEquals(null, stroke.segmentWidths)
    }

    @Test
    fun aCancelledStrokeIsDropped() {
        send(MotionEvent.ACTION_DOWN, stylus(300f, 400f))
        send(MotionEvent.ACTION_MOVE, stylus(400f, 400f))
        send(MotionEvent.ACTION_CANCEL, stylus(400f, 400f))
        assertTrue(edits.isEmpty())
        assertEquals(1, wetInk.cancels)
        // Also when the system flags the pointer as cancelled (e.g. a palm).
        send(MotionEvent.ACTION_DOWN, stylus(300f, 400f))
        send(MotionEvent.ACTION_MOVE, stylus(400f, 400f))
        send(MotionEvent.ACTION_UP, stylus(400f, 400f), flags = MotionEvent.FLAG_CANCELED)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun aPalmDoesNothingWhileWriting() {
        val before = viewport
        send(MotionEvent.ACTION_DOWN, stylus(300f, 400f))
        send(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            stylus(320f, 400f),
            finger(700f, 1200f, id = 1),
        )
        for (step in 1..STEPS) {
            send(
                MotionEvent.ACTION_MOVE,
                stylus(320f + 20f * step, 400f),
                finger(
                    700f,
                    1200f - 50f * step,
                    id = 1,
                ),
            )
        }
        send(
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            stylus(500f, 400f),
            finger(700f, 800f, id = 1),
        )
        send(MotionEvent.ACTION_UP, stylus(500f, 400f))
        assertEquals(1, edits.size)
        assertEquals(before, viewport)
    }

    @Test
    fun theEraserEndAndTheStylusButtonDontDraw() {
        send(MotionEvent.ACTION_DOWN, Pointer(0, MotionEvent.TOOL_TYPE_ERASER, 300f, 400f))
        send(MotionEvent.ACTION_MOVE, Pointer(0, MotionEvent.TOOL_TYPE_ERASER, 400f, 600f))
        send(MotionEvent.ACTION_UP, Pointer(0, MotionEvent.TOOL_TYPE_ERASER, 400f, 600f))
        send(MotionEvent.ACTION_DOWN, stylus(300f, 400f), buttons = MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(MotionEvent.ACTION_MOVE, stylus(400f, 600f), buttons = MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(MotionEvent.ACTION_UP, stylus(400f, 600f), buttons = MotionEvent.BUTTON_STYLUS_PRIMARY)
        assertTrue(edits.isEmpty())
        assertEquals(0.0, viewport.offsetY, 0.0)
    }

    @Test
    fun theStylusTakesOverAFingerPan() {
        send(MotionEvent.ACTION_DOWN, finger(500f, 1200f, id = 1))
        send(MotionEvent.ACTION_MOVE, finger(500f, 1150f, id = 1))
        val panned = viewport
        send(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            finger(500f, 1150f, id = 1),
            stylus(300f, 400f),
        )
        for (step in 1..STEPS) {
            send(
                MotionEvent.ACTION_MOVE,
                finger(500f, 1150f - 40f * step, id = 1),
                stylus(300f + 25f * step, 400f),
            )
        }
        send(
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            finger(500f, 800f, id = 1),
            stylus(500f, 400f),
        )
        send(MotionEvent.ACTION_UP, finger(500f, 800f, id = 1))
        assertEquals(1, edits.size)
        // The finger stopped panning when the stylus came down.
        assertEquals(panned, viewport)
    }

    @Test
    fun committedStrokesAreDrawnUntilTheTilesShowThem() {
        scheduler.advanceUntilIdle()
        write(300f, 400f, length = 400f)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        // Tiles not re-rendered yet: the stroke is drawn on top.
        view.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(500, 400))
        // Re-rendered: the tiles show it.
        scheduler.advanceUntilIdle()
        bitmap.eraseColor(0)
        view.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(500, 400))
    }

    @Test
    fun undonePagesAreShown() {
        scheduler.advanceUntilIdle()
        write(300f, 400f, length = 400f)
        val (index, written) = edits.single()
        // Undo at once: neither the pending stroke nor its wet ink stay.
        val clears = wetInk.clears
        view.showPages(mapOf(index to pages[index]))
        assertTrue(wetInk.clears > clears)
        scheduler.advanceUntilIdle()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(500, 400))
        // Redo: it is back, and new strokes go on the page shown.
        view.showPages(mapOf(index to written))
        scheduler.advanceUntilIdle()
        view.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(500, 400))
        write(300f, 600f)
        assertEquals(
            2,
            edits
                .last()
                .second.layers
                .single()
                .elements.size,
        )
    }

    private fun assertClose(
        expected: Double,
        actual: Double,
    ) = assertTrue("expected $expected, got $actual", abs(expected - actual) < 1e-3)

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 1400
        const val STEPS = 8
    }
}
