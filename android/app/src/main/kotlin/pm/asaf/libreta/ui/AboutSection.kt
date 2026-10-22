// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pm.asaf.libreta.R

/** Libreta's version, that it is free software without warranty, where its source is, and the licences. */
@Composable
internal fun AboutSection() {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var licences by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.about_version, versionName(context))) },
        supportingContent = { Text(stringResource(R.string.about_text)) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.about_source)) },
        supportingContent = { Text(SOURCE) },
        modifier = Modifier.clickable { uri.openUri(SOURCE) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.about_licences)) },
        supportingContent = { Text(stringResource(R.string.about_licences_summary)) },
        modifier = Modifier.clickable { licences = true },
    )
    if (licences) LicencesDialog { licences = false }
}

/** The parts of the app with their licences; one tapped shows its licence texts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicencesDialog(onClose: () -> Unit) {
    var shown by remember { mutableStateOf<Licensed?>(null) }
    val back = { if (shown == null) onClose() else shown = null }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(shown?.name ?: stringResource(R.string.about_licences)) },
                    navigationIcon = { BackButton(back) },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                val licensed = shown
                if (licensed == null) {
                    for (part in LICENSED) {
                        ListItem(
                            headlineContent = { Text(part.name) },
                            supportingContent = { Text(part.terms) },
                            modifier = Modifier.clickable { shown = part },
                        )
                    }
                } else {
                    val context = LocalContext.current
                    val text = remember(licensed) { licensed.files.joinToString("\n\n") { licenceText(context, it) } }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

private fun licenceText(
    context: Context,
    file: String,
): String =
    context.assets
        .open("licenses/$file")
        .bufferedReader()
        .use { it.readText() }

private fun versionName(context: Context): String =
    context.packageManager
        .getPackageInfo(context.packageName, 0)
        .versionName
        .orEmpty()

private const val SOURCE = "https://github.com/asafpm/Libreta"
