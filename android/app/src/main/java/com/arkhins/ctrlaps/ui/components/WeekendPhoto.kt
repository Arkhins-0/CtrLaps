package com.arkhins.ctrlaps.ui.components

import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.arkhins.ctrlaps.ui.screens.loadShrunk
import androidx.compose.material3.Text
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import android.graphics.Bitmap
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
 * The top of a weekend's page: its photo, fading into the page. A tap pulls it up large; admins change or remove it
 * there, and add one with the button when there is none. With no photo, everyone else sees nothing.
 */
@Composable
fun WeekendPhotoHeader(weekendId: String, photoUrl: String?, isAdmin: Boolean, pageColor: Color, onChanged: () -> Unit) {
    if (photoUrl == null && !isAdmin) return
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The picked photo, waiting in the crop page: only the framed part is uploaded.
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    fun upload(part: Bitmap) {
        busy = true
        error = null
        scope.launch {
            try {
                val file = File(context.cacheDir, "weekend-photo.jpg")
                withContext(Dispatchers.IO) { file.outputStream().use { part.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
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
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            cropping = withContext(Dispatchers.IO) { loadShrunk(context, uri, max = 2400) }
            if (cropping == null) error = "Could not read that picture."
        }
    }
    cropping?.let { src -> HeaderCropDialog(src, onCancel = { cropping = null }) { part -> cropping = null; upload(part) } }
    // Debug builds: `--es sheet cropheader` opens the crop page with the current photo (see DebugHooks).
    if (com.arkhins.ctrlaps.BuildConfig.DEBUG && photoUrl != null) {
        val asked by com.arkhins.ctrlaps.ui.DebugHooks.sheet.collectAsState()
        LaunchedEffect(asked) {
            if (asked != "cropheader") return@LaunchedEffect
            com.arkhins.ctrlaps.ui.DebugHooks.sheet.value = null
            val result = context.imageLoader.execute(coil.request.ImageRequest.Builder(context).data(app.api.absolute(photoUrl)).allowHardware(false).build())
            cropping = (result as? coil.request.SuccessResult)?.drawable?.toBitmap()
        }
    }
    val choose = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val remove = {
        busy = true
        scope.launch {
            runCatching { app.api.delete("/api/weekends/$weekendId/photo") }.onFailure { error = it.message }
            busy = false
            onChanged()
        }
        Unit
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photoUrl != null) {
            var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
            BoxWithConstraints(
                Modifier.clip(MaterialTheme.shapes.large).onGloballyPositioned { bounds = it.boundsInWindow() }.clickable(enabled = !busy) {
                    openPhoto(context, app.api.absolute(photoUrl), "Track photo", onChange = if (isAdmin) choose else null, onRemove = if (isAdmin) remove else null, wide = true, from = bounds)
                },
            ) {
                // The header's shape is the crop frame's, 16:9, so it shows exactly what was framed.
                FadingPhoto(photoUrl, pageColor, maxWidth * 9f / 16f)
            }
            if (busy) Text("Saving…", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
        } else {
            GhostButton(if (busy) "Uploading…" else "Add a track photo", enabled = !busy) { choose() }
        }
        ErrorText(error)
    }
}

