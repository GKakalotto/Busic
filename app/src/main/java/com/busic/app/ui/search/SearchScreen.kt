package com.busic.app.ui.search

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.busic.app.ui.AppIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.busic.app.ui.FlatIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.busic.app.BusicApp
import com.busic.app.data.Track
import com.busic.app.ui.PageTitle
import com.busic.app.ui.TrackRow
import com.busic.app.ui.quietClick
import com.busic.app.ui.theme.BgActive
import com.busic.app.ui.theme.BgBase
import com.busic.app.ui.theme.BgHighlight
import com.busic.app.ui.theme.Brand
import com.busic.app.ui.theme.TextSecondary
import com.busic.app.ui.theme.TextTertiary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val api = BusicApp.get().api
    private val player = BusicApp.get().player
    private val local = BusicApp.get().local
    val queries = local.queries

    val keyword = MutableStateFlow("")
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _searched = MutableStateFlow(false)
    val searched: StateFlow<Boolean> = _searched
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore
    private var page = 0
    private var query = ""
    private var request = 0
    private var job: Job? = null

    fun updateKeyword(value: String) {
        keyword.value = value
    }

    fun search(raw: String = keyword.value) {
        val text = raw.trim()
        if (text.isEmpty()) return
        keyword.value = text
        local.rememberQuery(text)
        query = text
        page = 0
        val token = ++request
        job?.cancel()
        job = viewModelScope.launch {
            _loading.value = true
            _loadingMore.value = false
            _hasMore.value = false
            _error.value = null
            _searched.value = true
            _tracks.value = emptyList()
            runCatching { api.search(text, 1) }
                .onSuccess { result ->
                    if (token != request) return@onSuccess
                    page = 1
                    _tracks.value = result.tracks
                    _hasMore.value = result.tracks.isNotEmpty() && page < result.numPages
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (token != request) return@onFailure
                    _error.value = error.message ?: "搜索失败"
                }
            if (token == request) _loading.value = false
        }
    }

    fun loadMore() {
        if (job?.isActive == true || !_hasMore.value || page <= 0) return
        val text = query
        val next = page + 1
        val token = request
        job = viewModelScope.launch {
            _loadingMore.value = true
            runCatching { api.search(text, next) }
                .onSuccess { result ->
                    if (token != request) return@onSuccess
                    if (result.tracks.isEmpty()) {
                        _hasMore.value = false
                    } else {
                        page = next
                        val seen = _tracks.value.map { it.bvid }.toHashSet()
                        _tracks.value = _tracks.value + result.tracks.filter { it.bvid !in seen }
                        _hasMore.value = page < result.numPages
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                }
            if (token == request) _loadingMore.value = false
        }
    }

    fun clear() {
        request++
        job?.cancel()
        keyword.value = ""
        _tracks.value = emptyList()
        _error.value = null
        _searched.value = false
        _loading.value = false
        _loadingMore.value = false
        _hasMore.value = false
        page = 0
        query = ""
    }

    fun clearQueries() {
        local.clearQueries()
    }

    fun play(index: Int) {
        player.play(_tracks.value, index)
    }
}

@Composable
fun SearchScreen(vm: SearchViewModel) {
    val keyword by vm.keyword.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val searched by vm.searched.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val queries by vm.queries.collectAsState()
    val player = BusicApp.get().player
    val playingBvid by remember(player) {
        player.state.map { it.track?.bvid }.distinctUntilChanged()
    }.collectAsState(initial = player.state.value.track?.bvid)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(
        Modifier
            .fillMaxSize()
            .background(BgBase),
    ) {
        PageTitle("搜索")
        OutlinedTextField(
            value = keyword,
            onValueChange = vm::updateKeyword,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("歌名、UP 主或关键词", color = TextTertiary) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                if (keyword.isNotEmpty()) {
                    FlatIconButton(onClick = vm::clear) {
                        Icon(AppIcons.Close, contentDescription = "清空", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                focusManager.clearFocus()
                vm.search()
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = BgActive,
                unfocusedContainerColor = BgHighlight,
                cursorColor = Brand,
            ),
        )
        if (queries.isNotEmpty() && tracks.isEmpty() && !loading) {
            SearchHistory(queries, onPick = vm::search, onClear = vm::clearQueries)
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = TextSecondary)
            }
            tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searched) "没有找到内容" else "输入后点键盘搜索",
                    color = TextTertiary,
                )
            }
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow {
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                        info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
                    }.distinctUntilChanged().collect { near ->
                        if (near) vm.loadMore()
                    }
                }
                LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(tracks, key = { index, item -> "${item.bvid}-$index" }) { index, track ->
                    TrackRow(
                        track = track,
                        active = track.bvid == playingBvid,
                        onClick = { vm.play(index) },
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
                            if (loadingMore) {
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistory(
    queries: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            queries.forEach { query ->
                Text(
                    text = query,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 160.dp)
                        .clip(RoundedCornerShape(50))
                        .background(BgHighlight)
                        .quietClick { onPick(query) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        FlatIconButton(onClick = onClear) {
            Icon(AppIcons.Delete, contentDescription = "删除搜索记录", tint = TextSecondary)
        }
    }
}
