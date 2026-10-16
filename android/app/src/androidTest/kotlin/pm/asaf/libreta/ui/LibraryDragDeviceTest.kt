// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.FolderState
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.library.PreviewCache
import pm.asaf.libreta.store.SafDocumentStore
import java.util.concurrent.CopyOnWriteArrayList

/** Moving cards by dragging them, with touches through the system, as the system drag needs. */
@RunWith(AndroidJUnit4::class)
class LibraryDragDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val root = FolderRef(StoreId("root"), "Notes")
    private val courses = LibraryEntry.Folder(FolderRef(StoreId("root/Courses"), "Courses"), 0, root)
    private val physics = LibraryEntry.Folder(FolderRef(StoreId("root/Physics"), "Physics"), 0, root)
    private val drops = CopyOnWriteArrayList<Pair<String, String>>()

    private fun show() {
        val context = instrumentation.targetContext
        val previews = PreviewCache(context, SafDocumentStore(context.contentResolver))
        val manage = ManageActions({}, {}, {}, drop = { entry, folder -> drops += entry.name to folder.name })
        val actions = LibraryActions({}, {}, {}, manage)
        val state = FolderState.Loaded(listOf(courses, physics))
        compose.setContent {
            MaterialTheme {
                LibraryGrid {
                    sectionHeader(root, "Notes", manage.drop, {}, {})
                    folderItems("root", state, previews, actions)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun centreOf(text: String): Offset {
        val node = compose.onNodeWithText(text).fetchSemanticsNode()
        return node.positionOnScreen + Offset(node.size.width / 2f, node.size.height / 2f)
    }

    /** A finger held on [from] until the drag starts, then moved to [to] and lifted there. */
    private fun drag(
        from: Offset,
        to: Offset,
    ) {
        val down = SystemClock.uptimeMillis()
        inject(down, MotionEvent.ACTION_DOWN, from)
        // The test's clock runs the app's timers: held long enough to pick the card up.
        compose.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout() + HOLD_MS)
        SystemClock.sleep(HOLD_MS)
        for (step in 1..STEPS) {
            inject(down, MotionEvent.ACTION_MOVE, from + (to - from) * (step / STEPS.toFloat()))
            SystemClock.sleep(STEP_MS)
        }
        SystemClock.sleep(HOLD_MS)
        inject(down, MotionEvent.ACTION_UP, to)
        compose.waitForIdle()
    }

    private fun inject(
        downTime: Long,
        action: Int,
        at: Offset,
    ) {
        val event =
            MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, at.x, at.y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
        instrumentation.uiAutomation.injectInputEvent(event, true)
        event.recycle()
    }

    @Test
    fun aCardDroppedOnAFolderMovesIntoIt() {
        show()
        drag(centreOf("Physics"), centreOf("Courses"))
        compose.waitUntil(TIMEOUT) { drops.isNotEmpty() }
        assertEquals(listOf("Physics" to "Courses"), drops.toList())
    }

    @Test
    fun aCardDroppedWhereItIsStays() {
        show()
        // Its own folder's heading, then itself.
        drag(centreOf("Courses"), centreOf("Notes"))
        drag(centreOf("Courses"), centreOf("Courses") + Offset(4f, 4f))
        // Then elsewhere, to tell that the drags were made.
        drag(centreOf("Courses"), centreOf("Physics"))
        compose.waitUntil(TIMEOUT) { drops.isNotEmpty() }
        assertEquals(listOf("Courses" to "Physics"), drops.toList())
    }

    private companion object {
        const val HOLD_MS = 300L
        const val STEPS = 20
        const val STEP_MS = 16L
        const val TIMEOUT = 5_000L
    }
}
