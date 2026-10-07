package com.arkhins.ctrlaps.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.arkhins.ctrlaps.ui.screens.loadShrunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Change a photo: the gallery, a square crop, then [upload] gets the cropped JPEG (deleted afterwards). Returns what
 * starts it; pass it as a photo's onChange (see [openPhoto]). [onError] gets what went wrong.
 */
@Composable
fun rememberPhotoChanger(upload: suspend (File) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { cropping = withContext(Dispatchers.IO) { loadShrunk(context, uri, 1600) } }
    }
    cropping?.let { src ->
        SquareCropDialog(src, onCancel = { cropping = null }) { square ->
            cropping = null
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "photo-${System.currentTimeMillis()}.jpg").also { f -> f.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                }
                try {
                    upload(file)
                } catch (e: Exception) {
                    onError(e.message ?: "Could not save the photo.")
                } finally {
                    file.delete()
                }
            }
        }
    }
    return { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
