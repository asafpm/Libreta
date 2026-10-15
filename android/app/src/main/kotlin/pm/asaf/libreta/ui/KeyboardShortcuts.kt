// SPDX-License-Identifier: GPL-2.0-or-later
// Xournal++ v1.3.8's keyboard shortcuts (ui/mainmenubar.xml, src/core/control/actions/
// ActionProperties.h, src/core/gui/XournalView.cpp: arrows move a selection 3 points, 1 with
// Alt, 10 with Shift; Space scrolls a screen), © Xournal++ contributors.

package pm.asaf.libreta.ui

import android.content.Context
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.DrawingType
import pm.asaf.libreta.core.model.SelectMode
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.files.NoteSession
import pm.asaf.libreta.viewer.DocumentView
import pm.asaf.libreta.viewer.Navigation

/** Where keyboard shortcuts go: the note shown, if any. */
class KeyShortcuts {
    var handler: ((KeyEvent) -> Boolean)? = null

    /** Handles [event] if it is a shortcut; false otherwise. */
    fun dispatch(event: KeyEvent): Boolean = handler?.invoke(event) == true
}

/** The activity's [KeyShortcuts]. */
val LocalKeyShortcuts = staticCompositionLocalOf<KeyShortcuts?> { null }

/** What the keyboard does in a note shown in [view]. */
class NoteKeys(
    private val view: () -> DocumentView?,
    private val session: NoteSession,
    private val tools: ToolControls,
    private val selection: SelectionActions,
    private val newPage: () -> Unit,
    private val export: (() -> Unit)?,
    private val insertImage: () -> Unit,
) {
    private fun tool(tool: Tool) = { tools.onTool(tool) }

    private fun select(mode: SelectMode) =
        {
            tools.onTool(Tool.SELECT)
            tools.onSelectMode(mode)
        }

    private fun shape(type: DrawingType) = { tools.onChange { it.copy(drawingType = type) } }

    private fun navigate(move: Navigation) = { view()?.navigate(move) ?: Unit }

    private val withCtrlShift: Map<Int, () -> Unit> =
        mapOf(
            KeyEvent.KEYCODE_Z to { redo(session, view()) },
            KeyEvent.KEYCODE_P to tool(Tool.PEN),
            KeyEvent.KEYCODE_E to tool(Tool.ERASER),
            KeyEvent.KEYCODE_H to tool(Tool.HIGHLIGHTER),
            KeyEvent.KEYCODE_T to tool(Tool.TEXT),
            KeyEvent.KEYCODE_R to select(SelectMode.RECTANGLE),
            KeyEvent.KEYCODE_G to select(SelectMode.LASSO),
            KeyEvent.KEYCODE_I to insertImage,
        )

    private val withCtrl: Map<Int, () -> Unit> =
        mapOf(
            KeyEvent.KEYCODE_Z to { undo(session, view()) },
            KeyEvent.KEYCODE_Y to { redo(session, view()) },
            KeyEvent.KEYCODE_X to selection.cut,
            KeyEvent.KEYCODE_C to selection.copy,
            KeyEvent.KEYCODE_V to { if (selection.canPaste) selection.paste() },
            KeyEvent.KEYCODE_D to newPage,
            KeyEvent.KEYCODE_E to { export?.invoke() ?: Unit },
            KeyEvent.KEYCODE_EQUALS to navigate(Navigation.ZoomBy(ZOOM_STEP)),
            KeyEvent.KEYCODE_PLUS to navigate(Navigation.ZoomBy(ZOOM_STEP)),
            KeyEvent.KEYCODE_NUMPAD_ADD to navigate(Navigation.ZoomBy(ZOOM_STEP)),
            KeyEvent.KEYCODE_MINUS to navigate(Navigation.ZoomBy(1 / ZOOM_STEP)),
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT to navigate(Navigation.ZoomBy(1 / ZOOM_STEP)),
            KeyEvent.KEYCODE_0 to navigate(Navigation.FitWidth),
            KeyEvent.KEYCODE_MOVE_HOME to navigate(Navigation.ToPage(0)),
            KeyEvent.KEYCODE_MOVE_END to navigate(Navigation.ToPage(Int.MAX_VALUE)),
            KeyEvent.KEYCODE_1 to shape(DrawingType.FREEHAND),
            KeyEvent.KEYCODE_2 to shape(DrawingType.RECTANGLE),
            KeyEvent.KEYCODE_3 to shape(DrawingType.ELLIPSE),
            KeyEvent.KEYCODE_4 to shape(DrawingType.ARROW),
            KeyEvent.KEYCODE_5 to shape(DrawingType.DOUBLE_ARROW),
            KeyEvent.KEYCODE_6 to shape(DrawingType.COORDINATE_SYSTEM),
            KeyEvent.KEYCODE_7 to shape(DrawingType.LINE),
        )

    private val plain: Map<Int, () -> Unit> =
        mapOf(
            KeyEvent.KEYCODE_PAGE_UP to navigate(Navigation.PageBy(-1)),
            KeyEvent.KEYCODE_PAGE_DOWN to navigate(Navigation.PageBy(1)),
            KeyEvent.KEYCODE_DEL to selection.delete,
            KeyEvent.KEYCODE_FORWARD_DEL to selection.delete,
            KeyEvent.KEYCODE_ESCAPE to { view()?.selection?.clear() ?: Unit },
        )

    /** Handles [event] (a key going down) if it is a shortcut. */
    fun handle(event: KeyEvent): Boolean {
        val code = event.keyCode
        val action =
            when {
                event.isCtrlPressed && event.isShiftPressed -> withCtrlShift[code]
                event.isCtrlPressed -> withCtrl[code]
                else -> plain[code] ?: moving(event)
            }
        action?.invoke()
        return action != null
    }

    /** Space scrolls a screen; arrows move the selection, or scroll. */
    private fun moving(event: KeyEvent): (() -> Unit)? {
        val (x, y) = ARROWS[event.keyCode] ?: (0 to 0)
        val selected = view()?.selection?.page != null
        return when {
            event.keyCode == KeyEvent.KEYCODE_SPACE -> {
                navigate(Navigation.ScreenBy(if (event.isShiftPressed) -1 else 1))
            }

            x == 0 && y == 0 -> {
                null
            }

            selected -> {
                val step =
                    when {
                        event.isAltPressed -> SMALL_MOVE
                        event.isShiftPressed -> LARGE_MOVE
                        else -> REGULAR_MOVE
                    }
                { view()?.selection?.moveBy(x * step, y * step) ?: Unit }
            }

            else -> {
                navigate(Navigation.ScrollBy(x * SCROLL_PX, y * SCROLL_PX))
            }
        }
    }

    private companion object {
        /** Each arrow's direction. */
        val ARROWS =
            mapOf(
                KeyEvent.KEYCODE_DPAD_LEFT to (-1 to 0),
                KeyEvent.KEYCODE_DPAD_RIGHT to (1 to 0),
                KeyEvent.KEYCODE_DPAD_UP to (0 to -1),
                KeyEvent.KEYCODE_DPAD_DOWN to (0 to 1),
            )
        const val REGULAR_MOVE = 3.0
        const val SMALL_MOVE = 1.0
        const val LARGE_MOVE = 10.0
        const val SCROLL_PX = 30f
        const val ZOOM_STEP = 1.1
    }
}

/** Sends the activity's keyboard shortcuts to [keys] while this is shown. */
@Composable
fun KeysFor(keys: NoteKeys) {
    val shortcuts = LocalKeyShortcuts.current ?: return
    val current = rememberUpdatedState(keys)
    DisposableEffect(shortcuts) {
        val handler: (KeyEvent) -> Boolean = { current.value.handle(it) }
        shortcuts.handler = handler
        onDispose { if (shortcuts.handler === handler) shortcuts.handler = null }
    }
}

/** The shortcuts, as Android lists them (Meta + /). */
fun shortcutGroups(context: Context): List<KeyboardShortcutGroup> {
    val ctrl = KeyEvent.META_CTRL_ON
    val ctrlShift = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON

    fun info(
        label: Int,
        key: Int,
        meta: Int = ctrl,
    ) = KeyboardShortcutInfo(context.getString(label), key, meta)
    val edit =
        listOf(
            info(R.string.undo, KeyEvent.KEYCODE_Z),
            info(R.string.redo, KeyEvent.KEYCODE_Y),
            info(R.string.cut, KeyEvent.KEYCODE_X),
            info(R.string.copy, KeyEvent.KEYCODE_C),
            info(R.string.paste, KeyEvent.KEYCODE_V),
            info(R.string.delete, KeyEvent.KEYCODE_FORWARD_DEL, 0),
            info(R.string.insert_page, KeyEvent.KEYCODE_D),
            info(R.string.insert_image, KeyEvent.KEYCODE_I, ctrlShift),
            info(R.string.export_pdf, KeyEvent.KEYCODE_E),
        )
    val tools =
        listOf(
            info(R.string.tool_pen, KeyEvent.KEYCODE_P, ctrlShift),
            info(R.string.tool_highlighter, KeyEvent.KEYCODE_H, ctrlShift),
            info(R.string.tool_eraser, KeyEvent.KEYCODE_E, ctrlShift),
            info(R.string.tool_text, KeyEvent.KEYCODE_T, ctrlShift),
            info(R.string.shortcut_select_rectangle, KeyEvent.KEYCODE_R, ctrlShift),
            info(R.string.shortcut_select_lasso, KeyEvent.KEYCODE_G, ctrlShift),
            info(R.string.shape_freehand, KeyEvent.KEYCODE_1),
            info(R.string.shape_rectangle, KeyEvent.KEYCODE_2),
            info(R.string.shape_ellipse, KeyEvent.KEYCODE_3),
            info(R.string.shape_arrow, KeyEvent.KEYCODE_4),
            info(R.string.shape_double_arrow, KeyEvent.KEYCODE_5),
            info(R.string.shape_axes, KeyEvent.KEYCODE_6),
            info(R.string.shape_line, KeyEvent.KEYCODE_7),
        )
    val view =
        listOf(
            info(R.string.zoom_in, KeyEvent.KEYCODE_EQUALS),
            info(R.string.zoom_out, KeyEvent.KEYCODE_MINUS),
            info(R.string.fit_width, KeyEvent.KEYCODE_0),
            info(R.string.previous_page, KeyEvent.KEYCODE_PAGE_UP, 0),
            info(R.string.next_page, KeyEvent.KEYCODE_PAGE_DOWN, 0),
            info(R.string.first_page, KeyEvent.KEYCODE_MOVE_HOME),
            info(R.string.last_page, KeyEvent.KEYCODE_MOVE_END),
        )
    return listOf(
        KeyboardShortcutGroup(context.getString(R.string.shortcuts_edit), edit),
        KeyboardShortcutGroup(context.getString(R.string.shortcuts_tools), tools),
        KeyboardShortcutGroup(context.getString(R.string.shortcuts_view), view),
    )
}
