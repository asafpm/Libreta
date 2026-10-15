// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import org.junit.Assert.assertNotEquals
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
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.PenSettings
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.Color as InkColor

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletInputTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val page = Page(595.0, 842.0, Background.Solid(InkColor.WHITE, PageStyle.PLAIN), listOf(Layer(emptyList())))
    private val pages = List(3) { page }
    private val scheduler = TestCoroutineScheduler()
    private lateinit var view: DocumentView

    @Before
    fun setUp() {
        view = DocumentView(context)
        val dispatcher = StandardTestDispatcher(scheduler)
        view.setDocument(
            pages,
            RenderCaches(TileCache(Long.MAX_VALUE), Thumbnails(Long.MAX_VALUE)),
            { PageRenderer(context) },
            RenderThreads(workers = 1, dispatcher, dispatcher),
        )
        view.pen = PenSettings(InkColor.of(255, 0, 0), PenSize.VERY_THICK)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, WIDTH, HEIGHT)
    }

    @After
    fun tearDown() = view.close()

    private fun motion(
        action: Int,
        x: Float,
        y: Float,
        source: Int = InputDevice.SOURCE_STYLUS,
        tool: Int = MotionEvent.TOOL_TYPE_STYLUS,
        meta: Int = 0,
        vertical: Float = 0f,
    ): MotionEvent {
        val properties =
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = tool
            }
        val coordinates =
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                setAxisValue(MotionEvent.AXIS_VSCROLL, vertical)
            }
        return MotionEvent.obtain(
            0,
            0,
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
            source,
            0,
        )
    }

    private fun drawn(): Bitmap =
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    @Test
    fun theHoveringPenShowsADotOfItsColour() {
        view.dispatchGenericMotionEvent(motion(MotionEvent.ACTION_HOVER_MOVE, 500f, 600f))
        val pixel = drawn().getPixel(500, 600)
        assertEquals(255, Color.red(pixel))
        assertTrue(Color.green(pixel) < 40)
        // Lifted away: gone.
        view.dispatchGenericMotionEvent(motion(MotionEvent.ACTION_HOVER_EXIT, 500f, 600f))
        assertNotEquals(pixel, drawn().getPixel(500, 600))
    }

    @Test
    fun theTextToolShowsATextCursor() {
        view.tools = view.tools.copy(tool = Tool.TEXT)
        view.dispatchGenericMotionEvent(motion(MotionEvent.ACTION_HOVER_MOVE, 500f, 600f))
        val pixel = drawn().getPixel(500, 600)
        assertTrue(Color.red(pixel) < 128)
    }

    @Test
    fun aMouseWheelScrollsAndZoomsWithCtrl() {
        val before = checkNotNull(view.viewport)
        view.dispatchGenericMotionEvent(
            motion(
                MotionEvent.ACTION_SCROLL,
                500f,
                600f,
                InputDevice.SOURCE_MOUSE,
                MotionEvent.TOOL_TYPE_MOUSE,
                vertical = -1f,
            ),
        )
        val scrolled = checkNotNull(view.viewport)
        assertTrue(scrolled.offsetY > before.offsetY)
        assertEquals(before.zoom, scrolled.zoom, 0.0)
        view.dispatchGenericMotionEvent(
            motion(
                MotionEvent.ACTION_SCROLL,
                500f,
                600f,
                InputDevice.SOURCE_MOUSE,
                MotionEvent.TOOL_TYPE_MOUSE,
                meta = KeyEvent.META_CTRL_ON,
                vertical = 1f,
            ),
        )
        assertEquals(scrolled.zoom * 1.1, checkNotNull(view.viewport).zoom, 1e-6)
    }

    @Test
    fun pagesAreReachedOneByOneOrAtTheEnds() {
        view.navigate(Navigation.PageBy(1))
        assertEquals(1, view.currentPage)
        view.navigate(Navigation.ToPage(Int.MAX_VALUE))
        assertEquals(2, view.currentPage)
        view.navigate(Navigation.ToPage(0))
        assertEquals(0, view.currentPage)
    }

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 1600
    }
}
