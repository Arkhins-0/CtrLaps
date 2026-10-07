package com.arkhins.ctrlaps.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arkhins.ctrlaps.ui.screens.AboutSheet

/** A profile photo pulled up: the name, the photo large and square, and Close. */
@Composable
fun PhotoPreviewHost() {
    val (url, name) = PhotoPreview.shown.value ?: return
    AboutSheet(name, onClose = { PhotoPreview.shown.value = null }, expanded = true) {
        AsyncImage(
            model = url,
            contentDescription = "$name's photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(1f).clip(RoundedCornerShape(24.dp)),
        )
    }
}
