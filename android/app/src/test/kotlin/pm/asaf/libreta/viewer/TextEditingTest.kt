// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
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
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.TextSettings
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.applying
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextEditingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val existing = Element.Text("Old text", "Serif Bold", 18.0, 100.0, 200.0, Color.of(200, 0, 0))
    private var document =
        Document(
            listOf(Page(595.0, 842.0, Background.Solid(Color.WHITE, PageStyle.PLAIN), listOf(Layer(listOf(existing))))),
        )
    private val layout = DocumentLayout(document.pages.map { Size(it.width, it.height) })
    private val scheduler = TestCoroutineScheduler()
    private val edits = ArrayList<Edit>()
    private val styles = ArrayList<TextSettings>()
    private lateinit var view: DocumentView
    private lateinit var field: EditText

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
        view.tools = view.tools.copy(tool = Tool.TEXT)
        view.onEdit = { edit ->
            edits += edit
            document = document.applying(edit)
        }
        view.text.onStyle = { styles += it }
        field = EditText(context)
        view.text.field = field
        val parent =
            FrameLayout(context).apply {
                addView(view)
                addView(
                    field,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        parent.layout(0, 0, WIDTH, HEIGHT)
    }

    @After
    fun tearDown() = view.close()

    private var time = 1_000L

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
        tool: Int = MotionEvent.TOOL_TYPE_STYLUS,
    ) {
        val properties =
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = tool
            }
        val coordinates =
            MotionEvent.PointerCoords().apply {
                x = point.first
                y = point.second
                pressure = 0.5f
            }
        val source =
            if (tool ==
                MotionEvent.TOOL_TYPE_FINGER
            ) {
                InputDevice.SOURCE_TOUCHSCREEN
            } else {
                InputDevice.SOURCE_STYLUS
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
                0,
                1f,
                1f,
                0,
                0,
                source,
                0,
            )
        view.dispatchTouchEvent(event)
        event.recycle()
        time += 10
    }

    private fun tap(
        x: Double,
        y: Double,
        tool: Int = MotionEvent.TOOL_TYPE_STYLUS,
    ) {
        send(MotionEvent.ACTION_DOWN, toView(x, y), tool)
        send(MotionEvent.ACTION_UP, toView(x, y), tool)
    }

    private fun texts() =
        document.pages[0]
            .layers[0]
            .elements
            .filterIsInstance<Element.Text>()

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 0.5,
    ) = assertTrue("expected $expected, got $actual", abs(expected - actual) <= tolerance)

    private val lineHeight get() = PageRenderer(context).lineMetrics("Sans", 12.0).lineHeight

    @Test
    fun aTapStartsTextWithItsFirstLineCentredThere() {
        tap(300.0, 400.0)
        assertEquals(View.VISIBLE, field.visibility)
        assertTrue(field.hasFocus())
        field.setText("Hello\nworld")
        view.text.finish()
        assertEquals(View.GONE, field.visibility)
        assertEquals(1, edits.size)
        val text = texts().last()
        assertEquals("Hello\nworld", text.text)
        assertEquals("Sans", text.font)
        assertEquals(12.0, text.size, 0.0)
        assertEquals(Color.BLACK, text.color)
        assertClose(300.0, text.x)
        assertClose(400.0 - lineHeight / 2, text.y)
    }

    @Test
    fun emptyNewTextIsNotAdded() {
        tap(300.0, 400.0)
        field.setText("   ")
        view.text.finish()
        assertTrue(edits.isEmpty())
    }

    @Test
    fun aTapOnTextEditsItInItsOwnStyle() {
        tap(110.0, 205.0)
        assertEquals("Old text", field.text.toString())
        assertEquals(listOf(TextSettings("Serif Bold", 18.0, Color.of(200, 0, 0))), styles)
        field.setText("New text")
        view.text.finish()
        assertEquals(1, edits.size)
        assertEquals(listOf(existing.copy(text = "New text")), texts())
        // Unchanged: no edit.
        tap(110.0, 205.0)
        view.text.finish()
        assertEquals(1, edits.size)
    }

    @Test
    fun textEmptiedIsDeleted() {
        tap(110.0, 205.0)
        field.setText("")
        view.text.finish()
        assertEquals(1, edits.size)
        assertTrue(texts().isEmpty())
    }

    @Test
    fun stylesChosenWhileEditingApply() {
        tap(300.0, 400.0)
        field.setText("Styled")
        view.tools = view.tools.copy(text = TextSettings("Monospace Italic", 24.0, Color.of(0, 0, 200)))
        view.text.finish()
        val text = texts().last()
        assertEquals("Monospace Italic", text.font)
        assertEquals(24.0, text.size, 0.0)
        assertEquals(Color.of(0, 0, 200), text.color)
    }

    @Test
    fun theFieldIsWhereTheTextIsAtTheZoomShown() {
        tap(110.0, 205.0)
        val zoom = checkNotNull(view.viewport).zoom
        val (x, y) = toView(existing.x, existing.y)
        assertEquals(x, field.translationX, 1f)
        assertEquals(y, field.translationY, 1f)
        assertEquals((existing.size * zoom).toFloat(), field.textSize, 0.01f)
    }

    @Test
    fun fingersTapToEditButStillScroll() {
        tap(110.0, 205.0, MotionEvent.TOOL_TYPE_FINGER)
        assertEquals("Old text", field.text.toString())
        view.text.finish()
        // A finger drag is a scroll, not a tap.
        send(MotionEvent.ACTION_DOWN, toView(300.0, 400.0), MotionEvent.TOOL_TYPE_FINGER)
        send(MotionEvent.ACTION_MOVE, toView(300.0, 350.0), MotionEvent.TOOL_TYPE_FINGER)
        send(MotionEvent.ACTION_UP, toView(300.0, 350.0), MotionEvent.TOOL_TYPE_FINGER)
        assertEquals(View.GONE, field.visibility)
    }

    @Test
    fun anotherToolFinishesTheText() {
        tap(300.0, 400.0)
        field.setText("Kept")
        view.tools = view.tools.copy(tool = Tool.PEN)
        assertEquals("Kept", texts().last().text)
        assertEquals(View.GONE, field.visibility)
    }

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 1600
    }
}
