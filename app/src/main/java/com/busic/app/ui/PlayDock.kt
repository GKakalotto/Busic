package com.busic.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.busic.app.BusicApp
import com.busic.app.data.formatClockMs
import com.busic.app.ui.theme.BgActive
import com.busic.app.ui.theme.Brand
import com.busic.app.ui.theme.TextSecondary

@Composable
fun PlayDock(
    playing: Boolean,
    onOpenPlayer: () -> Unit,
) {
    val player = BusicApp.get().player
    val state by player.state.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF010103))
            .navigationBarsPadding()
            .height(72.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(cover = state.track?.cover, playing = playing, onClick = onOpenPlayer)
        ProgressText(Modifier.padding(start = 10.dp))
        Spacer(Modifier.weight(1f))
        FlatIconButton(onClick = player::previous) {
            Icon(AppIcons.SkipPrevious, contentDescription = "上一首", tint = Color.White)
        }
        FlatIconButton(onClick = player::toggle) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Brand,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    if (state.playing) AppIcons.Pause else AppIcons.PlayArrow,
                    contentDescription = if (state.playing) "暂停" else "播放",
                    tint = Color.White,
                )
            }
        }
        FlatIconButton(onClick = player::next) {
            Icon(AppIcons.SkipNext, contentDescription = "下一首", tint = Color.White)
        }
    }
}

@Composable
private fun Poster(cover: String?, playing: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .quietClick(onClick),
        contentAlignment = Alignment.Center,
    ) {
        ProgressRing()
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .spinWhile(playing, cover),
        ) {
            VinylDisc(Modifier.fillMaxSize())
            if (!cover.isNullOrBlank()) {
                var shown by remember(cover) { mutableStateOf(false) }
                AsyncImage(
                    model = cover,
                    contentDescription = "正在播放",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (shown) 1f else 0f },
                    contentScale = ContentScale.Crop,
                    onSuccess = { shown = true },
                )
            }
        }
    }
}

@Composable
private fun ProgressRing() {
    val progress by BusicApp.get().player.progress.collectAsState()
    val duration = progress.durationMs
    val fraction = if (duration <= 0L) 0f else progress.positionMs.toFloat() / duration
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = BgActive,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        val played = fraction.coerceIn(0f, 1f)
        if (played > 0f) {
            drawArc(
                color = Brand,
                startAngle = -90f,
                sweepAngle = 360f * played,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun ProgressText(modifier: Modifier) {
    val progress by BusicApp.get().player.progress.collectAsState()
    Text(
        text = "${clock(progress.positionMs)}/${clock(progress.durationMs)}",
        color = TextSecondary,
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private fun clock(ms: Long): String = formatClockMs(ms).ifBlank { "0:00" }

@Composable
private fun VinylDisc(modifier: Modifier) {
    Canvas(modifier.clip(CircleShape)) {
        drawCircle(Color(0xFF16161A))
        repeat(5) { index ->
            drawCircle(
                color = Color(0xFF2A2A30),
                radius = size.minDimension / 2f * (0.42f + index * 0.1f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        drawCircle(color = Brand, radius = size.minDimension * 0.16f)
        drawCircle(color = Color(0xFF0A0A0C), radius = size.minDimension * 0.045f)
    }
}
