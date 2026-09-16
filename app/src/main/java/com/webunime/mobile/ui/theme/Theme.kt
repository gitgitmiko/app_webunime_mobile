package com.webunime.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WuBg = Color(0xFF141414)
private val WuSurface = Color(0xFF1F1F1F)
private val WuAccent = Color(0xFFE50914)
private val WuText = Color(0xFFFFFFFF)
private val WuMuted = Color(0xFFB3B3B3)

private val scheme = darkColorScheme(
    primary = WuAccent,
    onPrimary = WuText,
    background = WuBg,
    onBackground = WuText,
    surface = WuSurface,
    onSurface = WuText,
    onSurfaceVariant = WuMuted,
    secondary = WuMuted,
)

@Composable
fun WebunimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
