// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.StrokeTool
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class NoteClipboardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val system = checkNotNull(context.getSystemService(ClipboardManager::class.java))

    private val stroke =
        Element.Stroke(StrokeTool.PEN, Color.of(237, 83, 83), 2.26, PointList.of(100.0, 100.0, 200.0, 150.0))

    @Test
    fun elementsGoOnTheClipboard() {
        val clipboard = NoteClipboard(context)
        clipboard.copy(listOf(stroke))
        assertTrue(clipboard.canPaste.value)
        assertEquals(listOf(stroke), NoteClipboard(context).paste())
    }

    @Test
    fun aClosedClipboardNoLongerFollowsTheSystemOne() {
        system.setPrimaryClip(android.content.ClipData.newPlainText("text", "hello"))
        val clipboard = NoteClipboard(context)
        clipboard.close()
        // A picture copied in another app: no longer heard of.
        val description = android.content.ClipDescription("picture", arrayOf("image/png"))
        val item = android.content.ClipData.Item(android.net.Uri.parse("content://elsewhere/picture.png"))
        system.setPrimaryClip(android.content.ClipData(description, item))
        assertEquals(false, clipboard.canPaste.value)
    }

    @Test
    fun aBigCopyStaysInTheApp() {
        // A photo: megabytes as text, more than Android's clipboard takes (about 1 MB).
        val photo = Element.Image(Bounds(0.0, 0.0, 400.0, 300.0), Random(1).nextBytes(3_000_000).toByteString())
        val clipboard = NoteClipboard(context)
        clipboard.copy(listOf(photo, stroke))
        val text = checkNotNull(system.primaryClip?.getItemAt(0)?.text)
        assertTrue("${text.length} characters", text.length < 1_000)
        assertTrue(clipboard.canPaste.value)
        assertEquals(listOf(photo, stroke), clipboard.paste())
    }
}
