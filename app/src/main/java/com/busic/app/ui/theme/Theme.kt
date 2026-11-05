package com.busic.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgBase = Color(0xFF0A0A0C)
val BgElevated = Color(0xFF16161A)
val BgHighlight = Color(0xFF23232A)
val BgActive = Color(0xFF2E2E36)
val Brand = Color(0xFFFB7299)
val TextSecondary = Color(0xFF9E9EAF)
val TextTertiary = Color(0xFF6A6A78)
val ErrorRed = Color(0xFFE91429)

private val scheme = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    background = BgBase,
    onBackground = Color.White,
    surface = BgElevated,
    onSurface = Color.White,
    surfaceVariant = BgHighlight,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
)

@Composable
fun BusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
