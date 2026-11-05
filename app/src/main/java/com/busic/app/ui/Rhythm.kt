package com.busic.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.busic.app.player.AudioSpectrum
import com.busic.app.ui.theme.Brand
import kotlinx.coroutines.isActive

@Composable
fun Modifier.rhythmBars(playing: Boolean): Modifier {
    val levels = remember { FloatArray(AudioSpectrum.BAR_COUNT) }
    val shown = remember { FloatArray(AudioSpectrum.BAR_COUNT) }
    val clock = remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        AudioSpectrum.wanted = true
        onDispose { AudioSpectrum.wanted = false }
    }
    LaunchedEffect(playing) {
        var last = 0L
        while (isActive) {
            if (!playing && AudioSpectrum.settled()) break
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                last = now
                if (!playing) AudioSpectrum.decay()
                clock.floatValue = dt
            }
        }
    }
    return this.drawBehind {
        val follow = (clock.floatValue * 12f).coerceIn(0.12f, 0.35f)
        AudioSpectrum.read(levels)
        val gap = 4.dp.toPx()
        val count = shown.size
        val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2.dp.toPx())
        val maxH = size.height
        val minH = 3.dp.toPx()
        val total = barW * count + gap * (count - 1)
        var left = (size.width - total) / 2f
        for (index in shown.indices) {
            shown[index] += (levels[index] - shown[index]) * follow
            val level = shown[index].coerceIn(0f, 1f)
            val h = minH + (maxH - minH) * level
            drawRoundRect(
                color = Brand.copy(alpha = 0.45f + 0.55f * level),
                topLeft = Offset(left, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
            left += barW + gap
        }
    }
}
