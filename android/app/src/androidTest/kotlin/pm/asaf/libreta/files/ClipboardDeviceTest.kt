// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.libreta.AppViewModel
import pm.asaf.libreta.MainActivity

/**
 * Android lets an app read the clipboard only while it has focus: a picture copied in another
 * app, while Libreta is in the background, must be pastable once Libreta is back.
 */
@RunWith(AndroidJUnit4::class)
class ClipboardDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun aPictureCopiedWhileInTheBackgroundCanBePasted() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: AppViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[AppViewModel::class.java] }
            val clipboard = checkNotNull(model).clipboard
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            waitFor { scenario.state == Lifecycle.State.CREATED }
            // "Copied in another app": put on the clipboard while Libreta can't read it.
            val system = context.getSystemService(ClipboardManager::class.java)
            instrumentation.runOnMainSync {
                // Sensitive: kept out of the clipboard's history and previews where they respect it.
                val description =
                    ClipDescription("picture", arrayOf("image/png")).apply {
                        extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                    }
                val item = ClipData.Item(Uri.parse("content://pm.asaf.libreta.test/picture.png"))
                system.setPrimaryClip(ClipData(description, item))
            }
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
            waitFor { scenario.state == Lifecycle.State.RESUMED }
            try {
                waitFor { clipboard.canPaste.value }
                assertTrue(clipboard.canPaste.value)
            } finally {
                // Not left on the device's clipboard.
                instrumentation.runOnMainSync { system.clearPrimaryClip() }
            }
        }
    }

    private fun waitFor(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + TIMEOUT
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Timed out" }
            SystemClock.sleep(STEP_MS)
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val STEP_MS = 50L
    }
}
