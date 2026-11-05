package com.busic.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.busic.app.BusicApp
import com.busic.app.data.FavFolder
import com.busic.app.data.LocalStore
import com.busic.app.data.Track
import com.busic.app.ui.AppIcons
import com.busic.app.ui.ConfirmDialog
import com.busic.app.ui.TrackRow
import com.busic.app.ui.quietClick
import com.busic.app.ui.player.rememberPlayer
import com.busic.app.ui.theme.BgBase
import com.busic.app.ui.theme.Brand
import com.busic.app.ui.theme.TextSecondary
import com.busic.app.ui.theme.TextTertiary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun FavScreen(folder: FavFolder) {
    val recent = folder.id == LocalStore.RECENT_ID
    val api = BusicApp.get().api
    val local = BusicApp.get().local
    val history by local.recent.collectAsState()
    val player = rememberPlayer()
    val playingBvid by remember(player) {
        player.state.map { it.track?.bvid }.distinctUntilChanged()
    }.collectAsState(initial = player.state.value.track?.bvid)
    val cached = if (recent) null else FolderCache.get(folder.id)
    var loaded by remember(folder.id) { mutableStateOf(cached?.tracks ?: emptyList()) }
    var loadedPage by remember(folder.id) { mutableIntStateOf(cached?.page ?: 0) }
    var loadedMore by remember(folder.id) { mutableStateOf(cached?.hasMore ?: true) }
    var visible by remember(folder.id) { mutableIntStateOf(minOf(PAGE, cached?.tracks?.size ?: 0)) }
    var confirmClear by remember { mutableStateOf(false) }
    var loading by remember(folder.id) { mutableStateOf(!recent && cached == null) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun samePrefix(current: List<Track>, incoming: List<Track>): Boolean {
        if (incoming.isEmpty()) return current.isEmpty()
        if (incoming.size > current.size) return false
        for (i in incoming.indices) {
            if (incoming[i].bvid != current[i].bvid || incoming[i].title != current[i].title) return false
        }
        return true
    }

    suspend fun load(next: Int) {
        val result = runCatching { api.folderMedias(folder.id, next) }
        result.onSuccess { incoming ->
            if (next == 1 && samePrefix(loaded, incoming.tracks)) {
                if (loaded.size > incoming.tracks.size && loaded.size > PAGE) {
                    error = null
                    return@onSuccess
                }
                if (loaded.size == incoming.tracks.size) {
                    loadedMore = incoming.hasMore
                    error = null
                    return@onSuccess
                }
            }
            loaded = if (next == 1) incoming.tracks else loaded + incoming.tracks
            loadedPage = next
            loadedMore = incoming.hasMore
            visible = if (next == 1) loaded.size else minOf(loaded.size, visible + incoming.tracks.size)
            error = null
            FolderCache.put(folder.id, loaded, loadedPage, loadedMore)
        }.onFailure {
            if (next == 1 && loaded.isEmpty()) error = it.message ?: "加载失败"
        }
    }

    LaunchedEffect(folder.id) {
        if (recent) return@LaunchedEffect
        if (cached == null) {
            loading = true
            coroutineScope {
                val fetch = async { load(1) }
                delay(280)
                fetch.await()
            }
            loading = false
        } else {
            load(1)
        }
    }
    LaunchedEffect(folder.id, listState) {
        if (recent) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 1
        }.distinctUntilChanged().collect { near ->
            if (!near || loading || loadingMore || refreshing) return@collect
            if (visible < loaded.size) {
                visible = minOf(loaded.size, visible + PAGE)
                return@collect
            }
            if (!loadedMore) return@collect
            loadingMore = true
            load(loadedPage + 1)
            loadingMore = false
        }
    }
    val shown = if (recent) history else loaded.take(visible)

    Column(
        Modifier
            .fillMaxSize()
            .background(BgBase),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = folder.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (recent) {
                Box(
                    Modifier
                        .size(48.dp)
                        .quietClick { confirmClear = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Delete, contentDescription = "清除历史", tint = TextSecondary)
                }
            } else {
                Box(
                    Modifier
                        .size(48.dp)
                        .quietClick {
                            if (refreshing) return@quietClick
                            scope.launch {
                                val index = listState.firstVisibleItemIndex
                                val offset = listState.firstVisibleItemScrollOffset
                                val anchor = loaded.getOrNull(index)?.bvid
                                refreshing = true
                                load(1)
                                refreshing = false
                                if (anchor != null) {
                                    val at = loaded.take(visible).indexOfFirst { it.bvid == anchor }
                                    if (at >= 0 && at != index) listState.scrollToItem(at, offset)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Brand,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(AppIcons.Refresh, contentDescription = "同步", tint = Color.White)
                    }
                }
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = TextSecondary)
            }
            shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (recent) "还没有播放记录" else "收藏夹是空的", color = TextTertiary)
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = shown,
                        key = { index, item -> "${item.bvid}#$index" },
                        contentType = { _, _ -> "track" },
                    ) { index, track ->
                        TrackRow(
                            track = track,
                            active = track.bvid == playingBvid,
                            onClick = { player.play(shown, index) },
                        )
                    }
                    if (loadingMore) {
                        item(key = "more") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Brand,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "清除历史",
            body = "确定清除最近播放？",
            confirm = "清除",
            onConfirm = {
                confirmClear = false
                local.clearPlays()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

private const val PAGE = 20

private object FolderCache {
    data class Entry(val tracks: List<Track>, val page: Int, val hasMore: Boolean)

    private val pages = HashMap<Long, Entry>()

    fun get(id: Long): Entry? = pages[id]

    fun put(id: Long, tracks: List<Track>, page: Int, hasMore: Boolean) {
        pages[id] = Entry(tracks, page, hasMore)
    }
}
