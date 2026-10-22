// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.files.ImageImport
import pm.asaf.libreta.viewer.DocumentView
import java.io.File
import java.io.IOException

/** Putting pictures into the note: picked, taken with the camera (null if there is none), or dropped. */
class ImageActions(
    val insert: () -> Unit,
    /** A picture from elsewhere (e.g. the clipboard), centred in view. */
    val insertFrom: (Uri) -> Unit,
    val takePhoto: (() -> Unit)?,
    /** A picture dropped on the view, at a view point. */
    val drop: (uri: Uri, viewX: Float, viewY: Float) -> Unit,
)

/**
 * Inserting pictures into [view]'s page [page] (the one in view): they arrive selected, so the
 * tool becomes the selection tool ([onSelect]).
 */
@Composable
fun rememberImageActions(
    view: () -> DocumentView?,
    page: () -> Int,
    onSelect: () -> Unit,
): ImageActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photo = File(File(context.cacheDir, CAMERA).apply { mkdirs() }, "photo.jpg")
    val messages = LocalMessages.current
    val resources = LocalResources.current
    val failed = {
        val text = resources.getString(R.string.error_insert_image)
        messages?.say(text) ?: Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
    val insert: (
        Uri,
        Pair<Float, Float>?,
    ) -> Unit = { uri, at -> scope.insert(context, uri, at, view, page, onSelect, failed) }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { insert(it, null) }
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            if (taken) insert(Uri.fromFile(photo), null)
        }
    val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    return ImageActions(
        insert = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        insertFrom = { insert(it, null) },
        takePhoto =
            if (hasCamera) {
                { camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.exports", photo)) }
            } else {
                null
            },
        drop = { uri, x, y -> insert(uri, x to y) },
    )
}

/** Reads the picture at [uri] off the main thread, then adds it to the page (at [at] if given), selected. */
@Suppress("LongParameterList") // the picture, where it goes, and what follows
private fun CoroutineScope.insert(
    context: Context,
    uri: Uri,
    at: Pair<Float, Float>?,
    view: () -> DocumentView?,
    page: () -> Int,
    onSelect: () -> Unit,
    onFailed: () -> Unit,
) = launch {
    val image =
        try {
            withContext(Dispatchers.IO) { ImageImport.read(context, uri) }
        } catch (e: IOException) {
            Log.w(TAG, "Cannot insert $uri", e)
            onFailed()
            return@launch
        }
    onSelect()
    view()?.selection?.insertImage(page(), image.data, image.width, image.height, at)
}

private const val CAMERA = "camera"
private const val TAG = "ImageActions"
