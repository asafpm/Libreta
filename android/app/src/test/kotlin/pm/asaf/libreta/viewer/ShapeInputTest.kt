// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.DrawingType
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.StrokeTool
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.applying
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapeInputTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val page = Page(595.0, 842.0, Background.Solid(Color.WHITE, PageStyle.PLAIN), listOf(Layer(emptyList())))
    private var document = Document(listOf(page))
    private val layout = DocumentLayout(document.pages.map { Size(it.width, it.height) })
    private val scheduler = TestCoroutineScheduler()
    private val edits = ArrayList<Edit>()
    private lateinit var view: DocumentView

    @Before
    fun setUp() {
        view = DocumentView(context)
        val dispatcher = StandardTestDispatcher(scheduler)
        view.setDocument(
            document.pages,
            RenderCaches(TileCache(Long.MAX_VALUE), Thumbnails(Long.MAX_VALUE)),
            { PageRenderer(context) },
            RenderThreads(workers = 1, dispatcher, dispatcher),
        )
        view.onEdit = { edit ->
            edits += edit
            document = document.applying(edit)
        }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, WIDTH, HEIGHT)
    }

    @After
    fun tearDown() = view.close()

    private var time = 1_000L

    /** View pixels of page point ([x], [y]). */
    private fun toView(
        x: Double,
        y: Double,
    ): Pair<Float, Float> {
        val viewport = checkNotNull(view.viewport)
        val bounds = layout.pageBounds(0)
        val left = Math.round((bounds.left - viewport.offsetX) * viewport.zoom)
        val top = Math.round((bounds.top - viewport.offsetY) * viewport.zoom)
        return (left + x * viewport.zoom).toFloat() to (top + y * viewport.zoom).toFloat()
    }

    private fun send(
        action: Int,
        point: Pair<Float, Float>,
        meta: Int = 0,
        step: Long = 10,
    ) {
        val properties =
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_STYLUS
            }
        val coordinates =
            MotionEvent.PointerCoords().apply {
                x = point.first
                y = point.second
                pressure = 0.5f
            }
        val event =
            MotionEvent.obtain(
                1_000L,
                time,
                action,
                1,
                arrayOf(properties),
                arrayOf(coordinates),
                meta,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_STYLUS,
                0,
            )
        view.dispatchTouchEvent(event)
        event.recycle()
        time += step
    }

    /** A stylus drag in page units from ([x0], [y0]) to ([x1], [y1]), with keyboard [meta] state. */
    private fun drag(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
        meta: Int = 0,
    ) {
        send(MotionEvent.ACTION_DOWN, toView(x0, y0), meta)
        for (step in 1..STEPS) {
            send(MotionEvent.ACTION_MOVE, toView(x0 + (x1 - x0) * step / STEPS, y0 + (y1 - y0) * step / STEPS), meta)
        }
        send(MotionEvent.ACTION_UP, toView(x1, y1), meta)
    }

    private fun strokes() =
        document.pages[0]
            .layers
            .last()
            .elements
            .filterIsInstance<Element.Stroke>()

    private fun points(stroke: Element.Stroke) =
        (0 until stroke.points.size).map {
            stroke.points.x(it) to
                stroke.points.y(it)
        }

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 0.01,
    ) = assertTrue("expected $expected, got $actual", abs(expected - actual) <= tolerance)

    @Test
    fun aRectangleIsOneStrokeOnTheGrid() {
        view.tools = view.tools.copy(drawingType = DrawingType.RECTANGLE)
        // Near grid points: drawn on them.
        drag(7 * GRID + 0.6, 7 * GRID - 0.4, 14 * GRID - 0.3, 10 * GRID + 0.5)
        assertEquals(1, edits.size)
        val stroke = strokes().single()
        assertEquals(StrokeTool.PEN, stroke.tool)
        assertEquals(view.tools.pen.size.width, stroke.width, 0.0)
        assertNull(stroke.segmentWidths)
        val corners = points(stroke)
        assertEquals(5, corners.size)
        assertEquals(corners.first(), corners.last())
        assertClose(7 * GRID, corners[0].first)
        assertClose(7 * GRID, corners[0].second)
        assertClose(14 * GRID, corners[2].first)
        assertClose(10 * GRID, corners[2].second)
    }

    @Test
    fun linesSnapTheirAngleUnlessAltIsHeld() {
        view.tools = view.tools.copy(drawingType = DrawingType.LINE)
        drag(7 * GRID, 7 * GRID, 7 * GRID + 101, 7 * GRID + 2)
        val flat = points(strokes().single())
        assertClose(flat[0].second, flat[1].second)
        drag(7 * GRID, 20 * GRID, 7 * GRID + 101, 20 * GRID + 2, meta = KeyEvent.META_ALT_ON)
        val raw = points(strokes().last())
        assertClose(2.0, raw[1].second - raw[0].second, 0.2)
    }

    @Test
    fun theHighlighterDrawsShapesToo() {
        view.tools = view.tools.copy(tool = Tool.HIGHLIGHTER, drawingType = DrawingType.ELLIPSE)
        drag(7 * GRID, 7 * GRID, 14 * GRID, 10 * GRID)
        val stroke = strokes().single()
        assertEquals(StrokeTool.HIGHLIGHTER, stroke.tool)
        assertEquals(points(stroke).first(), points(stroke).last())
        assertTrue(stroke.points.size > 20)
    }

    @Test
    fun aTapDrawsNoShape() {
        view.tools = view.tools.copy(drawingType = DrawingType.RECTANGLE)
        send(MotionEvent.ACTION_DOWN, toView(7 * GRID, 7 * GRID))
        send(MotionEvent.ACTION_UP, toView(7 * GRID, 7 * GRID))
        assertTrue(edits.isEmpty())
    }

    @Test
    fun aFreehandStrokeHeldStillBecomesAStraightLine() {
        // A wobbly stroke, then the pen held still for 0.6 s, then moved on a little.
        val start = toView(100.0, 100.0)
        send(MotionEvent.ACTION_DOWN, start)
        for (step in 1..10) {
            send(
                MotionEvent.ACTION_MOVE,
                toView(
                    100.0 + step * 10,
                    100.0 + if (step % 2 == 0) 6.0 else -6.0,
                ),
            )
        }
        val held = toView(200.0, 106.0)
        repeat(7) { send(MotionEvent.ACTION_MOVE, held, step = 100) }
        send(MotionEvent.ACTION_MOVE, toView(220.0, 110.0))
        send(MotionEvent.ACTION_UP, toView(220.0, 110.0))
        assertEquals(1, edits.size)
        val line = points(strokes().single())
        assertEquals(2, line.size)
        assertClose(100.0, line[0].first, 0.5)
        assertClose(220.0, line[1].first, 1.0)
        // A stroke that keeps moving stays freehand.
        drag(100.0, 300.0, 300.0, 330.0)
        assertTrue(strokes().last().points.size > 2)
    }

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 1600
        const val STEPS = 8
        const val GRID = 14.17
    }
}
