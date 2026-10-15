// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.android.render.RenderCaches
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.core.model.Document
import pm.asaf.inkpad.core.model.NewPageTemplate
import pm.asaf.inkpad.core.model.PageOperations
import pm.asaf.inkpad.core.model.applying
import kotlin.math.roundToInt

/**
 * Page commands on the device, on the 104-page note: showing the rearranged document (on the
 * main thread) is quick, the page in view stays in view, and its tiles are kept. Writes
 * page-editing-report.txt (TestStorage).
 */
@RunWith(AndroidJUnit4::class)
class PageEditingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val debuggable =
        instrumentation.targetContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @Test
    fun rearrangingPagesIsQuickAndKeepsTheView() {
        val sample = physics()
        var document = sample.copy(pages = List(REPEATS) { sample.pages }.flatten().map { it.copy() })
        lateinit var view: DocumentView
        lateinit var caches: RenderCaches
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            view = DocumentView(activity)
            caches = RenderCaches.forDevice(activity)
            view.setDocument(document.pages, caches, { PageRenderer(activity.applicationContext) })
            activity.setContentView(view)
        }
        SystemClock.sleep(SETTLE_MS)
        instrumentation.runOnMainSync { view.scrollToPage(START) }
        SystemClock.sleep(SETTLE_MS)
        val shown = document.pages[START]
        val tiles = caches.tiles.size

        val timings = ArrayList<Double>()

        fun show(next: Document) {
            document = next
            instrumentation.runOnMainSync {
                val start = System.nanoTime()
                view.showDocument(next.pages)
                timings += (System.nanoTime() - start) / NANOS_PER_MS
            }
        }
        // Pages inserted before and after the one in view, moved, and deleted again.
        repeat(ROUNDS) {
            val current = document.pages.indexOfFirst { it === shown }
            show(
                document.applying(
                    PageOperations.insertPage(document, current + 1, NewPageTemplate().newPage(shown, current)),
                ),
            )
            show(document.applying(PageOperations.insertPage(document, 0, NewPageTemplate().newPage(shown, current))))
            show(document.applying(checkNotNull(PageOperations.movePage(document, document.pages.lastIndex, 1))))
            show(document.applying(checkNotNull(PageOperations.deletePage(document, 0))))
        }
        SystemClock.sleep(SETTLE_MS)
        val current = view.currentPage
        val sorted = timings.sorted()
        val report =
            buildString {
                val build = if (debuggable) "debuggable" else "release-like"
                appendLine(
                    "Page commands: ${timings.size} on ${document.pages.size} pages ($build build)",
                )
                appendLine(
                    "Showing the rearranged document: median ${"%.1f".format(
                        sorted[sorted.size / 2],
                    )} ms, max ${"%.1f".format(sorted.last())} ms",
                )
                appendLine(
                    "Page in view: ${current + 1} (was ${START + 1}; now at ${document.pages.indexOfFirst {
                        it === shown
                    } + 1})",
                )
                appendLine("Tiles: ${caches.tiles.size} (were $tiles)")
            }
        Log.i(TAG, report)
        TestStorage().openOutputFile("page-editing-report.txt").use { it.write(report.toByteArray()) }
        scenario.close()
        assertSame(report, shown, document.pages[current])
        assertTrue(report, sorted[(sorted.size * 0.95).roundToInt() - 1] < MAX_SHOW_MS)
        assertEquals(report, REPEATS * sample.pages.size + ROUNDS, document.pages.size)
    }

    private fun physics(): Document =
        instrumentation.context.assets
            .open("physics.xopp")
            .source()
            .buffer()
            .use { XoppReader.read(it.readByteString()).document }

    private companion object {
        const val TAG = "PageEditingDeviceTest"
        const val REPEATS = 13
        const val START = 50
        const val ROUNDS = 10
        const val SETTLE_MS = 2000L
        const val NANOS_PER_MS = 1_000_000.0

        /** A frame's worth. */
        const val MAX_SHOW_MS = 16.0
    }
}
