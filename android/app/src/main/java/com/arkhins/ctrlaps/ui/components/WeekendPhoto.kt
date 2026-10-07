package com.arkhins.ctrlaps.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.Ok
import com.arkhins.ctrlaps.data.PhotoShrink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A weekend's photo (the track), fading at the bottom into [fadeTo], the colour of what is below it. */
@Composable
fun FadingPhoto(url: String, fadeTo: Color, height: Dp, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    Box(modifier.fillMaxWidth().height(height)) {
        AsyncImage(model = app.api.absolute(url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to fadeTo.copy(alpha = 0.35f), 1f to fadeTo)))
    }
}

/**
 * The top of a weekend's page: its photo, fading into the page. Admins add, change or remove it here; with no photo,
 * everyone else sees nothing.
 */
@Composable
fun WeekendPhotoHeader(weekendId: String, photoUrl: String?, isAdmin: Boolean, pageColor: Color, onChanged: () -> Unit) {
    if (photoUrl == null && !isAdmin) return
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun upload(uri: Uri) {
        busy = true
        error = null
        scope.launch {
            try {
                val file = File(context.cacheDir, "weekend-photo.jpg")
                withContext(Dispatchers.IO) { PhotoShrink.shrink(context, uri, file) } ?: throw IllegalStateException("Could not read that picture.")
                app.api.postForm("/api/weekends/$weekendId/photo", emptyMap(), "photo" to file, "image/jpeg", Ok.serializer())
                file.delete()
                onChanged()
            } catch (e: Exception) {
                error = e.message ?: "Could not upload the photo."
            } finally {
                busy = false
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) upload(uri) }
    // Over the photo the buttons get a dark backing, so they read on a bright picture.
    val backing = if (photoUrl != null) Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape) else Modifier
    val controls: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (busy) "Uploading…" else if (photoUrl == null) "Add a track photo" else "Change photo", backing, enabled = !busy) {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (photoUrl != null) {
                GhostButton("Remove", backing, enabled = !busy, danger = true) {
                    busy = true
                    scope.launch {
                        runCatching { app.api.delete("/api/weekends/$weekendId/photo") }.onFailure { error = it.message }
                        busy = false
                        onChanged()
                    }
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photoUrl != null) {
            Box(Modifier.clip(MaterialTheme.shapes.large)) {
                FadingPhoto(photoUrl, pageColor, 180.dp)
                if (isAdmin) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { controls() }
            }
        } else {
            controls()
        }
        ErrorText(error)
    }
}
