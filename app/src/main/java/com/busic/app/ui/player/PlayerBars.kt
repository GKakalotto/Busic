package com.busic.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.busic.app.ui.AppIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.busic.app.ui.FlatIconButton
import com.busic.app.ui.quietClick
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.busic.app.BusicApp
import com.busic.app.ui.rhythmBars
import com.busic.app.data.formatClockMs
import com.busic.app.player.PlayMode
import com.busic.app.player.PlayerRepository
import com.busic.app.ui.theme.BgActive
import com.busic.app.ui.theme.BgBase
import com.busic.app.ui.theme.BgElevated
import com.busic.app.ui.theme.Brand
import com.busic.app.ui.theme.ErrorRed
import com.busic.app.ui.theme.TextSecondary

@Composable
fun rememberPlayer(): PlayerRepository = BusicApp.get().player

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(player: PlayerRepository = rememberPlayer()) {
    val state by player.state.collectAsState()
    val track = state.track
    var showList by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase)
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MarqueeTitle(track?.title.orEmpty())
        Text(
            track?.author.orEmpty(),
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        if (state.error != null) {
            Text(
                state.error ?: "",
                color = ErrorRed,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        Spacer(Modifier.weight(0.55f))
        AsyncImage(
            model = track?.cover,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-12).dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 36.dp)
                .height(156.dp)
                .rhythmBars(state.playing),
        )
        Spacer(Modifier.weight(1f))
        PlaySlider(player)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlatIconButton(onClick = player::cycleMode) {
                val tint = if (state.mode == PlayMode.ORDER) TextSecondary else Brand
                val icon = when (state.mode) {
                    PlayMode.SHUFFLE -> AppIcons.Shuffle
                    PlayMode.ONE -> AppIcons.RepeatOne
                    else -> AppIcons.Repeat
                }
                Icon(icon, contentDescription = state.mode.name, tint = tint)
            }
            FlatIconButton(onClick = player::previous) {
                Icon(AppIcons.SkipPrevious, contentDescription = "上一首", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            FlatIconButton(onClick = player::toggle, modifier = Modifier.size(72.dp)) {
                if (state.loading) {
                    CircularProgressIndicator(color = Brand, modifier = Modifier.size(36.dp))
                } else {
                    Icon(
                        if (state.playing) AppIcons.Pause else AppIcons.PlayArrow,
                        contentDescription = if (state.playing) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            FlatIconButton(onClick = player::next) {
                Icon(AppIcons.SkipNext, contentDescription = "下一首", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            FlatIconButton(onClick = { showList = true }) {
                Icon(AppIcons.QueueMusic, contentDescription = "播放列表", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        if (showList) {
            ModalBottomSheet(
                onDismissRequest = { showList = false },
                containerColor = BgElevated,
            ) {
                Text(
                    "播放列表",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                if (state.queue.isEmpty()) {
                    Text(
                        "还没有歌曲",
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        itemsIndexed(state.queue) { index, item ->
                            Text(
                                item.title,
                                color = if (index == state.index) Brand else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .quietClick {
                                        player.play(state.queue, index)
                                        showList = false
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaySlider(player: PlayerRepository) {
    val progress by player.progress.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val max = progress.durationMs.coerceAtLeast(1L).toFloat()
    val position = if (dragging) drag else progress.positionMs.toFloat().coerceIn(0f, max)
    Slider(
        value = position,
        onValueChange = {
            dragging = true
            drag = it
        },
        onValueChangeFinished = {
            player.seekTo(drag.toLong())
            dragging = false
        },
        valueRange = 0f..max,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        thumb = {
            Box(
                Modifier
                    .size(16.dp)
                    .background(Color.White, CircleShape),
            )
        },
        track = { sliderState ->
            val range = sliderState.valueRange
            val span = (range.endInclusive - range.start).coerceAtLeast(1f)
            val fraction = ((sliderState.value - range.start) / span).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                ) {
                    val y = size.height / 2f
                    val stroke = 10.dp.toPx()
                    drawLine(
                        color = BgActive,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Brand,
                        start = Offset(0f, y),
                        end = Offset(size.width * fraction, y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatClockMs(position.toLong()), color = TextSecondary, fontSize = 12.sp)
        Text(formatClockMs(progress.durationMs), color = TextSecondary, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarqueeTitle(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
        )
    }
}
