package com.arkhins.ctrlaps.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Near-black or white, whichever reads better on this colour (the higher WCAG contrast): text on a filled chip. */
fun Color.contrastText(): Color {
    val l = luminance()
    return if ((l + 0.05f) / 0.05f >= 1.05f / (l + 0.05f)) Color(0xFF0B0B0C) else Color.White
}
