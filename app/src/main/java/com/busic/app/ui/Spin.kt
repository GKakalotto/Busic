package com.busic.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.isActive

@Composable
fun Modifier.spinWhile(playing: Boolean, resetKey: Any?): Modifier {
    val angle = remember(resetKey) { Animatable(0f) }
    LaunchedEffect(playing, resetKey) {
        if (!playing) return@LaunchedEffect
        while (isActive) {
            val start = angle.value % 360f
            if (start != angle.value) angle.snapTo(start)
            val left = (360f - start).coerceAtLeast(0.5f)
            angle.animateTo(
                targetValue = 360f,
                animationSpec = tween(
                    durationMillis = (left / 360f * 18_000f).toInt().coerceAtLeast(16),
                    easing = LinearEasing,
                ),
            )
            angle.snapTo(0f)
        }
    }
    return this.graphicsLayer { rotationZ = angle.value }
}
