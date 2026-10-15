// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.content.Context
import android.view.InputDevice
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
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.SelectMode
import pm.asaf.libreta.core.model.StrokeTool
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.applying
import pm.asaf.libreta.core.model.reverting
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectionToolTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun line(
        x: Double,
        y: Double,
    ) = Element.Stroke(StrokeTool.PEN, Color.of(0, 0, 0), 2.0, PointList.of(x, y, x + 100, y))

    private val bottom = line(100.0, 100.0)
    private val b = line(100.0, 150.0)
    private val c = line(300.0, 250.0)

    // Two short pages, both in view: the first has a stroke on its lower layer, two on its top one.
    private val first =
        Page(
            595.0,
            400.0,
            Background.Solid(Color.WHITE, PageStyle.PLAIN),
            listOf(Layer(listOf(bottom)), Layer(listOf(b, c))),
        )
    private val second = first.copy(layers = listOf(Layer(emptyList())))
    private var document = Document(listOf(first, second))
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
        view.tools = view.tools.copy(tool = Tool.SELECT)
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

    /** View pixels of point ([x], [y]) of page [page]. */
    private fun toView(
        page: Int,
        x: Double,
        y: Double,
    ): Pair<Float, Float> {
        val viewport = checkNotNull(view.viewport)
        val bounds = layout.pageBounds(page)
        val left = Math.round((bounds.left - viewport.offsetX) * viewport.zoom)
        val top = Math.round((bounds.top - viewport.offsetY) * viewport.zoom)
        return (left + x * viewport.zoom).toFloat() to (top + y * viewport.zoom).toFloat()
    }

    private val zoom get() = checkNotNull(view.viewport).zoom

    private fun send(
        action: Int,
        point: Pair<Float, Float>,
        buttons: Int = 0,
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
                0,
                buttons,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_STYLUS,
                0,
            )
        view.dispatchTouchEvent(event)
        event.recycle()
        time += 10
    }

    /** A stylus drag through [points] (view pixels); [buttons] for each point. */
    private fun drag(
        points: List<Pair<Float, Float>>,
        buttons: List<Int> = points.map { 0 },
    ) {
        send(MotionEvent.ACTION_DOWN, points.first(), buttons.first())
        for (i in 1 until points.size) send(MotionEvent.ACTION_MOVE, points[i], buttons[i])
        send(MotionEvent.ACTION_UP, points.last(), buttons.last())
    }

    /** A straight drag from [from] to [to] in [steps]. */
    private fun dragLine(
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
        steps: Int = 8,
    ) = drag(
        (0..steps).map {
            from.first + (to.first - from.first) * it / steps to
                from.second + (to.second - from.second) * it / steps
        },
    )

    private fun selectRectangle(
        page: Int,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ) = dragLine(toView(page, x1, y1), toView(page, x2, y2))

    private fun topElements(page: Int = 0) =
        document.pages[page]
            .layers
            .last()
            .elements

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 1.0,
    ) = assertTrue("expected $expected, got $actual", abs(expected - actual) <= tolerance)

    @Test
    fun rectanglesSelectWholeElementsOnTheSelectedLayer() {
        // Around the lower layer's stroke and b: only b, on the top layer.
        selectRectangle(0, 90.0, 90.0, 210.0, 160.0)
        assertEquals(0, view.selection.page)
        assertEquals(listOf(b), view.selection.elements)
        // Part of c only: nothing.
        selectRectangle(0, 290.0, 240.0, 350.0, 260.0)
        assertNull(view.selection.page)
        selectRectangle(0, 50.0, 50.0, 450.0, 300.0)
        assertEquals(listOf(0, 1), view.selection.indices)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun lassosAndTapsSelect() {
        view.tools = view.tools.copy(selectMode = SelectMode.LASSO)
        val loop =
            listOf(280.0 to 230.0, 420.0 to 230.0, 420.0 to 270.0, 280.0 to 270.0, 280.0 to 231.0).map { (x, y) ->
                toView(0, x, y)
            }
        drag(loop)
        assertEquals(listOf(c), view.selection.elements)
        // A tap on b picks it, near it too.
        drag(listOf(toView(0, 150.0, 153.0)))
        assertEquals(listOf(b), view.selection.elements)
        drag(listOf(toView(0, 500.0, 350.0)))
        assertNull(view.selection.page)
    }

    @Test
    fun movingIsOneEdit() {
        selectRectangle(0, 90.0, 140.0, 210.0, 160.0)
        dragLine(toView(0, 150.0, 150.0), toView(0, 200.0, 180.0))
        assertEquals(1, edits.size)
        val moved = topElements()[0] as Element.Stroke
        assertClose(150.0, moved.points.x(0))
        assertClose(180.0, moved.points.y(0))
        // Still selected, where it is now.
        assertEquals(listOf(moved), view.selection.elements)
        // The other layer and element are as they were.
        assertEquals(listOf(bottom), document.pages[0].layers[0].elements)
        assertEquals(c, topElements()[1])
    }

    @Test
    fun cornersScaleAndTheRedHandleRotates() {
        selectRectangle(0, 290.0, 240.0, 410.0, 260.0)
        val box = checkNotNull(view.selection.bounds)
        // The bottom-right corner, along the diagonal to twice the size.
        dragLine(box.right to box.bottom, box.right + box.width() to box.bottom + box.height())
        val scaled = topElements()[1] as Element.Stroke
        assertClose(200.0, scaled.points.x(1) - scaled.points.x(0), 2.0)
        assertClose(4.0, scaled.width, 0.1)
        // A quarter turn: the rotation handle (right of the box) taken to below the centre.
        val turned = checkNotNull(view.selection.bounds)
        val handle = turned.right + (14 + 16) * context.resources.displayMetrics.density to turned.centerY()
        dragLine(handle, turned.centerX() to turned.bottom + 300)
        val rotated = topElements()[1] as Element.Stroke
        assertClose(rotated.points.x(0), rotated.points.x(1))
        assertEquals(2, edits.size)
    }

    @Test
    fun selectionsMoveToAnotherPageOrGoBack() {
        selectRectangle(0, 90.0, 140.0, 210.0, 160.0)
        // Dropped above the first page, off any page: back where it was.
        dragLine(toView(0, 150.0, 150.0), toView(0, 150.0, -30.0))
        assertTrue(edits.isEmpty())
        assertEquals(listOf(b, c), topElements())
        // Onto the second page: there, on top.
        dragLine(toView(0, 150.0, 150.0), toView(1, 150.0, 100.0))
        assertEquals(1, edits.size)
        assertEquals(listOf(c), topElements(0))
        val moved = topElements(1).single() as Element.Stroke
        assertClose(100.0, moved.points.y(0))
        assertEquals(1, view.selection.page)
    }

    @Test
    fun theStylusButtonStampsCopiesWhileMoving() {
        selectRectangle(0, 90.0, 140.0, 210.0, 160.0)
        val path = (0..12).map { toView(0, 150.0 + it * 10, 150.0 + it * 15) }
        // Pressed twice on the way.
        val buttons = (0..12).map { if (it in 4..5 || it in 8..9) MotionEvent.BUTTON_STYLUS_PRIMARY else 0 }
        drag(path, buttons)
        assertEquals(1, edits.size)
        // The original moved, two copies where the button was pressed, on top.
        val top = topElements()
        assertEquals(4, top.size)
        val ys = top.map { (it as Element.Stroke).points.y(0) }
        assertClose(330.0, ys[0])
        assertEquals(c, top[1])
        assertClose(210.0, ys[2])
        assertClose(270.0, ys[3])
        // One undo step for it all.
        assertEquals(
            listOf(b, c),
            document
                .reverting(edits.single())
                .pages[0]
                .layers
                .last()
                .elements,
        )
    }

    @Test
    fun anotherToolClearsTheSelection() {
        selectRectangle(0, 90.0, 140.0, 210.0, 160.0)
        assertEquals(0, view.selection.page)
        view.tools = view.tools.copy(tool = Tool.PEN)
        assertNull(view.selection.page)
    }

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 1600
    }
}
