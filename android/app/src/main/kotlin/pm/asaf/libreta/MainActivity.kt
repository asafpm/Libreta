// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.ui.KeyShortcuts
import pm.asaf.libreta.ui.LibretaApp
import pm.asaf.libreta.ui.LibretaTheme
import pm.asaf.libreta.ui.LocalKeyShortcuts
import pm.asaf.libreta.ui.shortcutGroups

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()

    /** Keyboard shortcuts, for the note shown. */
    private val shortcuts = KeyShortcuts()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a rotation the note is open already.
        if (savedInstanceState == null) openFrom(intent)
        setContent {
            val theme by model.appearance.theme.collectAsStateWithLifecycle()
            val darkPages by model.appearance.darkPages.collectAsStateWithLifecycle()
            LibretaTheme(theme, darkPages) {
                CompositionLocalProvider(LocalKeyShortcuts provides shortcuts) { LibretaApp(model) }
            }
        }
    }

    /**
     * Shortcuts come first, unless text is being typed (the text field has its own keys).
     * Activity.dispatchKeyEvent is public; androidx marks its override restricted, but passing
     * events on to it is what it is for.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val shortcut = event.action == KeyEvent.ACTION_DOWN && currentFocus !is EditText && shortcuts.dispatch(event)
        return shortcut || super.dispatchKeyEvent(event)
    }

    /** The shortcuts, listed by Android (Meta + /). */
    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>,
        menu: Menu?,
        deviceId: Int,
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data.addAll(shortcutGroups(this))
    }

    /** Leaving the app (or turning the screen off) saves the open note. */
    override fun onStop() {
        super.onStop()
        model.saveNote()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /** "Open with" from another app. */
    private fun openFrom(intent: Intent) {
        val uri = intent.data
        if (intent.action == Intent.ACTION_VIEW && uri != null) model.openExternal(StoreId(uri.toString()))
    }
}
