// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import pm.asaf.inkpad.ui.InkpadApp

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a rotation the note is open already.
        if (savedInstanceState == null) openFrom(intent)
        setContent {
            MaterialTheme {
                InkpadApp(model)
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
        if (intent.action == Intent.ACTION_VIEW && uri != null) model.openExternal(uri)
    }
}
