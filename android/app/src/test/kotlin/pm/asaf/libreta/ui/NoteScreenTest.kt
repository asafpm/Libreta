// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import pm.asaf.libreta.AppViewModel
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.android.render.RenderThreads
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.DrawingType
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.EraserSettings
import pm.asaf.libreta.core.model.EraserType
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.files.Autosave
import pm.asaf.libreta.library.FakeDocumentsProvider
import pm.asaf.libreta.library.LibraryRoot
import pm.asaf.libreta.library.LibraryRoots
import pm.asaf.libreta.viewer.DocumentView
import pm.asaf.libreta.viewer.LayerView
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val temporary = TemporaryFolder()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val testData = File(checkNotNull(System.getProperty("libreta.testdata")))
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        val preferences = application.getSharedPreferences("library", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        File(testData, "note-taking.xopp").copyTo(File(directory, "Lecture.xopp"))
        LibraryRoots(preferences).add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
    }

    private fun eventually(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + TIMEOUT
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out" }
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(10)
        }
    }

    private fun documentView(view: View): DocumentView? =
        view as? DocumentView
            ?: (view as? ViewGroup)?.let { group ->
                (0 until group.childCount).firstNotNullOfOrNull { documentView(group.getChildAt(it)) }
            }

    private fun stylus(
        view: DocumentView,
        action: Int,
        downTime: Long,
        time: Long,
        x: Float,
        y: Float,
    ) {
        val properties =
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_STYLUS
            }
        val coordinates =
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = 0.5f
            }
        val event =
            MotionEvent.obtain(
                downTime,
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
                InputDevice.SOURCE_STYLUS,
                0,
            )
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    /** Opens Lecture.xopp in the app, rendering paused. */
    private fun openLecture(autosave: Autosave = Autosave()): AppViewModel {
        val paused = StandardTestDispatcher()
        val model = AppViewModel(application, Dispatchers.IO, RenderThreads(workers = 1, paused, paused), autosave)
        compose.setContent {
            MaterialTheme { CompositionLocalProvider(LocalKeyShortcuts provides shortcuts) { LibretaApp(model) } }
        }
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Lecture.xopp")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Lecture.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }
        return model
    }

    /** Writes a horizontal stroke a third of the way down the view. */
    private fun write(
        view: DocumentView,
        downTime: Long = 1000,
    ) {
        compose.runOnIdle {
            val y = view.height / 3f
            stylus(view, MotionEvent.ACTION_DOWN, downTime, downTime, 200f, y)
            for (step in 1..8) {
                stylus(view, MotionEvent.ACTION_MOVE, downTime, downTime + step * 10, 200f + step * 30f, y)
            }
            stylus(view, MotionEvent.ACTION_UP, downTime, downTime + 100, 440f, y)
        }
    }

    private fun AppViewModel.elements(): List<Element> =
        (note.value as NoteState.Loaded)
            .document.pages[0]
            .layers
            .last()
            .elements

    @Test
    fun theChosenPenWritesTheNextStroke() {
        val model = openLecture()

        // The toolbar scrolls on narrow screens.
        compose.onNodeWithContentDescription("Colour: Red").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Width: Thick").performScrollTo().performClick()
        assertEquals(Color.of(237, 83, 83), model.tools.tools.value.pen.color)
        // The flow reaches the view through the main thread.
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        eventually { view.pen.size == PenSize.THICK }
        write(view)
        val session = (model.note.value as NoteState.Loaded).session
        val stroke =
            session.document.pages[0]
                .layers
                .last()
                .elements
                .last() as Element.Stroke
        assertEquals(Color.of(237, 83, 83), stroke.color)
        assertEquals(PenSize.THICK.width, stroke.width, 0.0)
        assertTrue(session.dirty)
        // Remembered for next time.
        assertEquals(
            PenSize.THICK,
            AppViewModel(application)
                .tools.tools.value.pen.size,
        )
    }

    @Test
    fun undoAndRedoAreSavedAndReopened() {
        val model = openLecture()
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        val before = model.elements().size
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        write(view, downTime = 1000)
        write(view, downTime = 2000)
        assertEquals(before + 2, model.elements().size)

        compose.onNodeWithContentDescription("Undo").performClick()
        assertEquals(before + 1, model.elements().size)
        compose.onNodeWithContentDescription("Undo").performClick()
        assertEquals(before, model.elements().size)
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Redo").performClick()
        assertEquals(before + 1, model.elements().size)
        val kept = model.elements().last()
        compose.onNodeWithContentDescription("Redo").assertIsEnabled()

        // Leaving saves; the note opens as it was left, with nothing to undo.
        compose.runOnIdle { model.back() }
        eventually { model.note.value == null }
        compose.onNodeWithText("Lecture.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }
        assertEquals(before + 1, model.elements().size)
        assertEquals(kept, model.elements().last())
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Redo").assertIsNotEnabled()
    }

    @Test
    fun eachToolKeepsItsOwnSettings() {
        val model = openLecture()
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        compose.onNodeWithContentDescription("Highlighter").performClick()
        compose.onNodeWithContentDescription("Colour: Bright yellow").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Width: Thick").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Eraser").performScrollTo().performClick()
        compose.onNodeWithText("Whiteout").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Width: Very fine").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Pen").performScrollTo().performClick()

        val tools = model.tools.tools.value
        assertEquals(Tool.PEN, tools.tool)
        assertEquals(PenSize.MEDIUM, tools.pen.size)
        assertEquals(PenSize.THICK, tools.highlighter.size)
        assertEquals(EraserSettings(EraserType.WHITEOUT, PenSize.VERY_FINE), tools.eraser)
        compose.onNodeWithContentDescription("Width: Medium").performScrollTo().assertIsSelected()
        // The view writes with them, and they are remembered.
        eventually { view.tools == tools }
        assertEquals(tools, AppViewModel(application).tools.tools.value)
    }

    @Test
    fun pickedColoursAreUsedAndRemembered() {
        val model = openLecture()
        compose.onNodeWithContentDescription("Add a colour").performScrollTo().performClick()
        compose.onNodeWithTag("hex").performTextReplacement("#123456")
        compose.onNodeWithText("OK").performClick()
        val picked = Color.of(0x12, 0x34, 0x56)
        assertEquals(picked, model.tools.tools.value.pen.color)
        compose.onNodeWithContentDescription("Colour: #123456").performScrollTo().assertIsSelected()
        // Still offered after choosing another colour, and next time.
        compose.onNodeWithContentDescription("Colour: Red").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Colour: #123456").performScrollTo().assertIsNotSelected()
        assertEquals(listOf(picked), AppViewModel(application).colors.recentColors.value)
    }

    @Test
    fun theToolbarOffersTheFixedAndThreeRecentColours() {
        openLecture()
        for (hex in listOf("#111111", "#222222", "#333333", "#444444")) {
            compose.onNodeWithContentDescription("Add a colour").performScrollTo().performClick()
            compose.onNodeWithTag("hex").performTextReplacement(hex)
            compose.onNodeWithText("OK").performClick()
        }
        val swatches = compose.onAllNodes(hasContentDescription("Colour:", substring = true)).fetchSemanticsNodes()
        val names = swatches.map { it.config[SemanticsProperties.ContentDescription].single().removePrefix("Colour: ") }
        assertEquals(
            listOf("Black", "Royal blue", "Red", "Dark green", "Bright yellow", "#444444", "#333333", "#222222"),
            names,
        )
    }

    @Test
    fun theToolbarOffersTheWidthsChosen() {
        val model = openLecture()
        compose.runOnIdle {
            model.tools.setSizeVisible(PenSize.VERY_FINE, false)
            model.tools.setSizeVisible(PenSize.MEDIUM, false)
        }
        val widths = compose.onAllNodes(hasContentDescription("Width:", substring = true)).fetchSemanticsNodes()
        val names = widths.map { it.config[SemanticsProperties.ContentDescription].single().removePrefix("Width: ") }
        // Medium stays while the pen uses it.
        assertEquals(listOf("Fine", "Medium", "Thick", "Very thick"), names)
        compose.onNodeWithContentDescription("Width: Thick").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Width: Medium").assertDoesNotExist()
    }

    private fun AppViewModel.pages() = (note.value as NoteState.Loaded).session.document.pages

    private fun clickFirst(description: String) =
        compose.onAllNodesWithContentDescription(description)[0].performClick()

    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun pagesAreInsertedDuplicatedMovedAndDeleted() {
        val model = openLecture()
        val first = model.pages().single()
        compose.onNodeWithContentDescription("Pages").performClick()
        compose.onNodeWithContentDescription("Page 1").assertIsSelected()
        clickFirst("Insert a page after this one")
        compose.waitForIdle()
        // Like the page it follows, empty; and in view.
        val pages = model.pages()
        assertEquals(2, pages.size)
        assertEquals(first.background, pages[1].background)
        assertEquals(listOf(0), pages[1].layers.map { it.elements.size })
        compose.onNodeWithContentDescription("Page 2").assertIsSelected()

        // Page 2 held and dragged onto page 1.
        val above = compose.onNodeWithContentDescription("Page 1").getBoundsInRoot()
        val held = compose.onNodeWithContentDescription("Page 2").getBoundsInRoot()
        val distance = with(compose.density) { (above.top - held.top).toPx() }
        compose.onNodeWithContentDescription("Page 2").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(DRAG_STEPS) { moveBy(Offset(0f, distance / DRAG_STEPS)) }
            up()
        }
        assertEquals(first, model.pages()[1])
        clickFirst("Duplicate page")
        assertEquals(3, model.pages().size)
        clickFirst("Delete page")
        assertEquals(2, model.pages().size)

        // A graph background on every page.
        clickFirst("Page background")
        compose.onNodeWithText("Graph").performClick()
        compose.onNodeWithText("Apply to all pages").performClick()
        compose.onNodeWithText("OK").performClick()
        assertTrue(model.pages().all { (it.background as Background.Solid).style == PageStyle.GRAPH })

        // All undone, step by step.
        repeat(5) { compose.onNodeWithContentDescription("Undo").performClick() }
        assertEquals(listOf(first), model.pages())
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun layersAreChosenHiddenRenamedAndReordered() {
        val model = openLecture()
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        val session = (model.note.value as NoteState.Loaded).session
        val before = model.pages()[0].layers.map { it.elements.size }
        compose.onNodeWithContentDescription("Layers").performClick()
        // The note's layers: Exercise below Solution. Write on Exercise, hide Solution.
        compose.onNodeWithContentDescription("Write on Exercise").performClick()
        compose.onNodeWithContentDescription("Hide Solution").performClick()
        assertEquals(LayerView(0, setOf(1)), session.layers.value[0])
        compose.onNodeWithText("Exercise").performClick()
        compose.onNodeWithTag("layer name").performTextReplacement("Notes")
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Add a layer above").performClick()
        // The new layer, between the two, is the one written on.
        assertEquals(listOf("Notes", null, "Solution"), model.pages()[0].layers.map { it.name })
        assertEquals(LayerView(1, setOf(2)), session.layers.value[0])
        compose.onNodeWithContentDescription("Write on Layer 2").assertIsSelected()
        compose.onNodeWithText("Delete layer").performClick()
        compose.onNodeWithText("Move layer up").performClick()
        assertEquals(listOf("Solution", "Notes"), model.pages()[0].layers.map { it.name })
        assertEquals(LayerView(1, setOf(0)), session.layers.value[0])
        compose.onNodeWithText("Move layer down").performClick()
        // Touches go to the page view, under the sheet.
        write(view)
        assertEquals(listOf(before[0] + 1, before[1]), model.pages()[0].layers.map { it.elements.size })
        assertEquals(view.layerViews, session.layers.value)
    }

    @Test
    fun theFingerButtonCanBeLeftOut() {
        val model = openLecture()
        compose.onNodeWithText("Draw with finger").performScrollTo().performClick()
        assertEquals(true, model.tools.fingerDraws.value)
        compose.runOnIdle { model.tools.setFingerButton(false) }
        compose.onNodeWithText("Draw with finger").assertDoesNotExist()
        assertEquals(false, model.tools.fingerDraws.value)
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun selectionsAreCopiedDeletedPastedDuplicatedAndRestyled() {
        val model = openLecture()
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        val original = model.pages()
        compose.onNodeWithContentDescription("Select").performClick()
        compose.waitForIdle()
        // A rectangle over the whole page: everything on the layer written on.
        compose.runOnIdle {
            // From just inside the page's top-left corner (selections start on a page) to its bottom right.
            val viewport = checkNotNull(view.viewport)
            val page = model.pages()[0]
            val x0 = (-viewport.offsetX * viewport.zoom + 40).toFloat()
            val y0 = (-viewport.offsetY * viewport.zoom + 40).toFloat()
            val x1 = (x0 + page.width * viewport.zoom - 80).toFloat()
            val y1 = (y0 + page.height * viewport.zoom - 80).toFloat()
            stylus(view, MotionEvent.ACTION_DOWN, 3000, 3000, x0, y0)
            for (step in 1..8) {
                stylus(
                    view,
                    MotionEvent.ACTION_MOVE,
                    3000,
                    3000L + step * 10,
                    x0 + (x1 - x0) * step / 8,
                    y0 + (y1 - y0) * step / 8,
                )
            }
            stylus(view, MotionEvent.ACTION_UP, 3000, 3100, x1, y1)
        }

        fun layerSize() =
            model
                .pages()[0]
                .layers
                .last()
                .elements.size
        val all = layerSize()
        val selected = view.selection.elements
        assertTrue(selected.isNotEmpty())
        compose.onNodeWithText("Copy").performClick()
        assertTrue(model.clipboard.canPaste.value)
        compose.onNodeWithText("Delete").performClick()
        assertEquals(all - selected.size, layerSize())
        // Pasted back, centred in view, on top: selected.
        compose.onNodeWithContentDescription("Paste").performClick()
        assertEquals(all, layerSize())
        assertEquals(selected.size, view.selection.elements.size)
        compose.onNodeWithText("Duplicate").performClick()
        assertEquals(all + selected.size, layerSize())
        // A colour from the tool bar recolours the selection's strokes.
        compose.onNodeWithContentDescription("Colour: Red").performScrollTo().performClick()
        assertTrue(
            view.selection.elements
                .filterIsInstance<Element.Stroke>()
                .all { it.color == Color.of(237, 83, 83) },
        )
        // All undone, step by step.
        repeat(4) { compose.onNodeWithContentDescription("Undo").performClick() }
        assertEquals(original, model.pages())
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun exportingAsksWhereWithTheNotesName() {
        openLecture()
        compose.onNodeWithContentDescription("Export PDF…").performClick()
        val picker = Shadows.shadowOf(compose.activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.action)
        assertEquals("application/pdf", picker.type)
        assertEquals("Lecture.pdf", picker.getStringExtra(Intent.EXTRA_TITLE))
        compose.onNodeWithContentDescription("Share as PDF").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun insertingAPictureOpensThePicker() {
        openLecture()
        compose.onNodeWithContentDescription("Insert image").performClick()
        val picker = Shadows.shadowOf(compose.activity).nextStartedActivityForResult.intent
        // The photo picker, or the documents picker where there is none.
        assertTrue(
            picker.action,
            picker.action in
                setOf(MediaStore.ACTION_PICK_IMAGES, Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT),
        )
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun picturesCopiedInOtherAppsPaste() {
        val model = openLecture()
        val png = temporary.newFile("copied.png")
        png.outputStream().use {
            Bitmap
                .createBitmap(80, 60, Bitmap.Config.ARGB_8888)
                .apply {
                    eraseColor(android.graphics.Color.BLUE)
                }.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val clip = ClipData(ClipDescription("Picture", arrayOf("image/png")), ClipData.Item(Uri.fromFile(png)))
        compose.runOnIdle { application.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip) }
        compose.onNodeWithContentDescription("Paste").performClick()
        eventually { model.elements().any { it is Element.Image } }
        val image = model.elements().filterIsInstance<Element.Image>().single()
        assertEquals(80.0, image.bounds.right - image.bounds.left, 0.01)
        assertEquals(Tool.SELECT, model.tools.tools.value.tool)
    }

    private val shortcuts = KeyShortcuts()

    /** A key pressed with [meta] (e.g. Ctrl), as the activity hands it on. */
    private fun press(
        key: Int,
        meta: Int = 0,
    ) = compose.runOnIdle { shortcuts.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0, meta)) }

    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun theKeyboardChoosesToolsAndShapesAndUndoes() {
        val model = openLecture()
        val ctrl = KeyEvent.META_CTRL_ON
        assertTrue(press(KeyEvent.KEYCODE_T, ctrl or KeyEvent.META_SHIFT_ON))
        assertEquals(Tool.TEXT, model.tools.tools.value.tool)
        press(KeyEvent.KEYCODE_P, ctrl or KeyEvent.META_SHIFT_ON)
        press(KeyEvent.KEYCODE_3, ctrl)
        assertEquals(DrawingType.ELLIPSE, model.tools.tools.value.drawingType)
        press(KeyEvent.KEYCODE_1, ctrl)
        // Written, undone, redone.
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        val before = model.elements().size
        write(view)
        compose.waitForIdle()
        assertEquals(before + 1, model.elements().size)
        press(KeyEvent.KEYCODE_Z, ctrl)
        assertEquals(before, model.elements().size)
        press(KeyEvent.KEYCODE_Y, ctrl)
        assertEquals(before + 1, model.elements().size)
        // Zoom in; keys that are no shortcut are left alone.
        val zoom = checkNotNull(view.viewport).zoom
        press(KeyEvent.KEYCODE_EQUALS, ctrl)
        assertEquals(zoom * 1.1, checkNotNull(view.viewport).zoom, 1e-6)
        assertFalse(press(KeyEvent.KEYCODE_Q))
    }

    @Test
    fun theShortcutsAreListed() {
        val groups = shortcutGroups(application)
        assertEquals(3, groups.size)
        assertTrue(groups.flatMap { it.items }.any { it.label == "Undo" && it.keycode == KeyEvent.KEYCODE_Z })
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val DRAG_STEPS = 10
    }
}
