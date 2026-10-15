// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.ui.LibretaApp
import pm.asaf.libreta.ui.LibretaTheme

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a rotation the note is open already.
        if (savedInstanceState == null) openFrom(intent)
        setContent {
            val theme by model.appearance.theme.collectAsStateWithLifecycle()
            val darkPages by model.appearance.darkPages.collectAsStateWithLifecycle()
            LibretaTheme(theme, darkPages) {
                LibretaApp(model)
            }
        }
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
