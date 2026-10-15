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
import pm.asaf.libreta.core.geometry.DocumentLayout
import pm.asaf.libreta.core.geometry.Size
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.EraserSettings
import pm.asaf.libreta.core.model.EraserType
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageChange
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.PenSettings
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.StrokeTool
import pm.asaf.libreta.core.model.StylusButton
import pm.asaf.libreta.core.model.Tool
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
    private val allEdits = ArrayList<Edit>()
    private lateinit var view: DocumentView

    @Before
    fun setUp() {
        view = DocumentView(context)
        val dispatcher = StandardTestDispatcher(scheduler)
        view.setDocument(pages, caches, { PageRenderer(context) }, RenderThreads(workers = 1, dispatcher, dispatcher))
        view.wetInk = wetInk
        view.pen = PenSettings(Color.of(255, 0, 0), PenSize.THICK)
        view.onEdit = { edit ->
            allEdits += edit
            edit.changes.filterIsInstance<PageChange>().forEach { edits += it.index to it.after }
        }
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

    /** A vertical sweep at [x] across y = 300 to 500, by [pointer] at each point. */
    private fun sweep(
        x: Float,
        buttons: Int = 0,
        pointer: (Float, Float) -> Pointer = ::stylus,
    ) {
        send(MotionEvent.ACTION_DOWN, pointer(x, 300f), buttons = buttons)
        for (step in 1..STEPS) send(MotionEvent.ACTION_MOVE, pointer(x, 300f + 200f * step / STEPS), buttons = buttons)
        send(MotionEvent.ACTION_UP, pointer(x, 500f), buttons = buttons)
    }

    private fun eraserEnd(
        x: Float,
        y: Float,
    ) = Pointer(0, MotionEvent.TOOL_TYPE_ERASER, x, y)

    /** The top layer's strokes in the last edit. */
    private fun erasedStrokes(): List<Element.Stroke> =
        allEdits
            .last()
            .changes
            .single()
            .let { it as PageChange }
            .after.layers
            .last()
            .elements
            .map { it as Element.Stroke }

    @Test
    fun theEraserEndCutsStrokes() {
        write(300f, 400f, length = 400f)
        sweep(500f, pointer = ::eraserEnd)
        // One edit for the gesture, cutting the stroke in two.
        assertEquals(2, allEdits.size)
        val pieces = erasedStrokes()
        assertEquals(2, pieces.size)
        val written =
            allEdits
                .first()
                .changes
                .single()
                .let { it as PageChange }
                .after
        assertTrue(pieces.all { it.color == Color.of(255, 0, 0) && it.width == PenSize.THICK.width })
        assertTrue(
            allEdits
                .last()
                .changes
                .single()
                .let { it as PageChange }
                .before === written,
        )
        // Nothing to erase: no edit.
        sweep(900f, pointer = ::eraserEnd)
        assertEquals(2, allEdits.size)
    }

    @Test
    fun holdingTheStylusButtonErasesUnlessTurnedOff() {
        write(300f, 400f, length = 400f)
        sweep(500f, buttons = MotionEvent.BUTTON_STYLUS_PRIMARY)
        assertEquals(2, erasedStrokes().size)
        assertEquals(0.0, viewport.offsetY, 0.0)

        view.tools = view.tools.copy(button = StylusButton.NONE)
        sweep(600f, buttons = MotionEvent.BUTTON_STYLUS_PRIMARY)
        assertEquals(StrokeTool.PEN, erasedStrokes().last().tool)
        assertEquals(3, erasedStrokes().size)
    }

    @Test
    fun theEraserToolDeletesStrokesOrPaintsWhite() {
        write(300f, 400f, length = 400f)
        write(300f, 450f, length = 400f)
        view.tools = view.tools.copy(tool = Tool.ERASER, eraser = EraserSettings(EraserType.DELETE_STROKE))
        // Just touching the second stroke.
        send(MotionEvent.ACTION_DOWN, stylus(500f, 455f))
        send(MotionEvent.ACTION_UP, stylus(500f, 455f))
        // The first stroke is left as it was.
        val first =
            allEdits
                .first()
                .changes
                .single()
                .let { it as PageChange }
                .after.layers
                .last()
                .elements
                .single()
        assertTrue(erasedStrokes().single() === first)

        view.tools = view.tools.copy(eraser = EraserSettings(EraserType.WHITEOUT, PenSize.THICK))
        sweep(500f)
        val whiteout = erasedStrokes().last()
        assertEquals(StrokeTool.ERASER, whiteout.tool)
        assertEquals(Color.of(255, 255, 255), whiteout.color)
        assertEquals(12.0, whiteout.width, 0.0)
        assertEquals(null, whiteout.segmentWidths)
    }

    @Test
    fun erasedInkGoesAtOnce() {
        scheduler.advanceUntilIdle()
        write(300f, 400f, length = 400f)
        scheduler.advanceUntilIdle()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(500, 400))
        // Tiles not re-rendered yet: the erased area is drawn from the page.
        sweep(500f, pointer = ::eraserEnd)
        view.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(500, 400))
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(350, 400))
        scheduler.advanceUntilIdle()
        view.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(500, 400))
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(350, 400))
    }

    @Test
    fun theEraserCursorShowsWhileHovering() {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val hover =
            MotionEvent.obtain(
                time,
                time,
                MotionEvent.ACTION_HOVER_MOVE,
                1,
                arrayOf(MotionEvent.PointerProperties().apply { toolType = MotionEvent.TOOL_TYPE_ERASER }),
                arrayOf(
                    MotionEvent.PointerCoords().apply {
                        x = 500f
                        y = 400f
                    },
                ),
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_STYLUS,
                0,
            )
        view.dispatchGenericMotionEvent(hover)
        hover.recycle()
        view.draw(Canvas(bitmap))
        // The square's edge: half a medium eraser (8.5 pt) from the centre.
        val half = Math.round(8.5 * viewport.zoom).toInt()
        assertEquals(0xFF404040.toInt(), bitmap.getPixel(500 + half, 400))
    }

    @Test
    fun highlightersAreDrawnInTheViewAsTheyWillLook() {
        scheduler.advanceUntilIdle()
        view.tools = view.tools.copy(tool = Tool.HIGHLIGHTER)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        send(MotionEvent.ACTION_DOWN, stylus(300f, 400f))
        for (step in 1..STEPS) send(MotionEvent.ACTION_MOVE, stylus(300f + 400f * step / STEPS, 400f))
        // Not wet ink: the view draws it, blended with the page.
        assertTrue(wetInk.segments.isEmpty())
        view.draw(Canvas(bitmap))
        val writing = bitmap.getPixel(500, 400)
        assertTrue(Integer.toHexString(writing), android.graphics.Color.blue(writing) < 200)
        assertEquals(255, android.graphics.Color.red(writing))
        send(MotionEvent.ACTION_UP, stylus(700f, 400f))

        val stroke = erasedStrokes().single()
        assertEquals(StrokeTool.HIGHLIGHTER, stroke.tool)
        assertEquals(Color.of(255, 255, 0), stroke.color)
        assertEquals(8.5, stroke.width, 0.0)
        assertEquals(null, stroke.segmentWidths)
        // The same before and after the tiles show it.
        view.draw(Canvas(bitmap))
        assertEquals(writing, bitmap.getPixel(500, 400))
        scheduler.advanceUntilIdle()
        view.draw(Canvas(bitmap))
        assertEquals(writing, bitmap.getPixel(500, 400))
    }

    @Test
    fun theStylusButtonCanHighlight() {
        view.tools = view.tools.copy(button = StylusButton.HIGHLIGHTER)
        sweep(500f, buttons = MotionEvent.BUTTON_STYLUS_PRIMARY)
        assertEquals(StrokeTool.HIGHLIGHTER, erasedStrokes().single().tool)
        sweep(600f)
        assertEquals(StrokeTool.PEN, erasedStrokes().last().tool)
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
        view.showDocument(pages)
        assertTrue(wetInk.clears > clears)
        scheduler.advanceUntilIdle()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(500, 400))
        // Redo: it is back, and new strokes go on the page shown.
        view.showDocument(pages.toMutableList().also { it[index] = written })
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

    @Test
    fun rearrangedPagesKeepTheViewAndTheirTiles() {
        // Page 2 in view, rendered.
        view.scrollToPage(1)
        scheduler.advanceUntilIdle()
        val tiles = caches.tiles.size
        assertTrue(tiles > 0)
        val second = pages[1]
        // A page inserted first: the same page stays in view, its tiles kept (now page 3).
        val inserted = listOf(blank.copy(), pages[0], second)
        view.showDocument(inserted)
        assertEquals(2, view.currentPage)
        assertEquals(tiles, caches.tiles.size)
        val kept = mutableListOf<Int>()
        caches.tiles.forEachOf(2) { key, _ -> kept += key.page }
        assertTrue(kept.isNotEmpty())
        // Deleted, then back (undo): still there.
        view.showDocument(listOf(pages[0], second))
        assertEquals(1, view.currentPage)
        // Ink goes on the page under it, in the new order.
        write(300f, 400f)
        assertEquals(1, edits.last().first)
    }

    @Test
    fun inkGoesOnTheSelectedLayerAndHiddenLayersAreNotDrawn() {
        view.scrollToPage(1)
        scheduler.advanceUntilIdle()
        // Page 2 has two layers: write on the lower one.
        view.layerViews = listOf(LayerView.of(pages[0]), LayerView(selected = 0))
        val y = ((layout.pageBounds(1).top + 100 - viewport.offsetY) * viewport.zoom).toFloat()
        write(300f, y, length = 400f)
        val (index, page) = edits.last()
        assertEquals(1, index)
        assertEquals(listOf(1, 0), page.layers.map { it.elements.size })
        scheduler.advanceUntilIdle()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(500, y.toInt()))
        // Hidden: not drawn, at once and once the tiles are rendered again.
        view.layerViews = listOf(LayerView.of(pages[0]), LayerView(selected = 1, hidden = setOf(0)))
        view.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(500, y.toInt()))
        scheduler.advanceUntilIdle()
        view.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(500, y.toInt()))
        // Writing on a hidden layer shows it again.
        val shown = mutableListOf<LayerView>()
        view.onLayerView = { _, layerView -> shown += layerView }
        view.layerViews = listOf(LayerView.of(pages[0]), LayerView(selected = 0, hidden = setOf(0)))
        write(300f, y + 100, length = 400f)
        assertEquals(listOf(LayerView(0)), shown)
        assertEquals(LayerView(0), view.layerViews[1])
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
