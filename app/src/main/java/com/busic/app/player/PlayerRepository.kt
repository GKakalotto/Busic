package com.busic.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.busic.app.data.BiliApi
import com.busic.app.data.LocalStore
import com.busic.app.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

enum class PlayMode { ORDER, LOOP, ONE, SHUFFLE }

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val track: Track? = null,
    val playing: Boolean = false,
    val mode: PlayMode = PlayMode.LOOP,
    val loading: Boolean = false,
    val error: String? = null,
)

data class PlayProgress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

class PlayerRepository(
    context: Context,
    private val api: BiliApi,
    private val local: LocalStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state
    private val _progress = MutableStateFlow(PlayProgress())
    val progress: StateFlow<PlayProgress> = _progress

    private var controller: MediaController? = null
    private var loadJob: Job? = null
    private var failJob: Job? = null
    private var failBvid = ""
    private var failStamp = 0L
    private var failedInRow = 0
    private var ticker: Job? = null
    private var token = 0
    private var uiActive = true

    init {
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java),
        )
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        future.addListener({
            runCatching { future.get() }
                .onSuccess { bind(it) }
                .onFailure { error ->
                    Log.e(TAG, "controller", error)
                    _state.update { it.copy(error = "播放器连接失败") }
                }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun play(queue: List<Track>, index: Int) {
        if (queue.isEmpty() || index !in queue.indices) return
        failedInRow = 0
        failBvid = ""
        _state.update { it.copy(queue = queue, index = index, error = null) }
        start(index)
    }

    fun toggle() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        val position = positionMs.coerceAtLeast(0)
        controller?.seekTo(position)
        _progress.value = _progress.value.copy(positionMs = position)
    }

    fun setUiActive(active: Boolean) {
        if (uiActive == active) return
        uiActive = active
        syncTicker()
    }

    fun next() = move(1)

    fun previous() {
        val player = controller
        if (player != null && player.currentPosition > 3_000) {
            player.seekTo(0)
            return
        }
        move(-1)
    }

    fun cycleMode() {
        _state.update {
            val next = when (it.mode) {
                PlayMode.ORDER -> PlayMode.LOOP
                PlayMode.LOOP -> PlayMode.ONE
                PlayMode.ONE -> PlayMode.SHUFFLE
                PlayMode.SHUFFLE -> PlayMode.ORDER
            }
            it.copy(mode = next)
        }
    }

    private fun move(step: Int) {
        val snapshot = _state.value
        if (snapshot.queue.isEmpty()) return
        val target = when (snapshot.mode) {
            PlayMode.SHUFFLE -> randomIndex(snapshot.queue.size, snapshot.index)
            else -> {
                val raw = snapshot.index + step
                when (snapshot.mode) {
                    PlayMode.ORDER -> raw
                    else -> floorMod(raw, snapshot.queue.size)
                }
            }
        }
        if (target !in snapshot.queue.indices) return
        start(target)
    }

    private fun start(index: Int) {
        val queue = _state.value.queue
        val track = queue.getOrNull(index) ?: return
        val my = ++token
        failJob?.cancel()
        loadJob?.cancel()
        if (failBvid != track.bvid) {
            failBvid = track.bvid
            failStamp = SystemClock.elapsedRealtime()
        }
        _state.update { it.copy(index = index, track = track, loading = true, error = null) }
        loadJob = scope.launch {
            while (isActive && token == my) {
                try {
                    val (ready, url) = api.audioUrl(track)
                    if (my != token) return@launch
                    val player = controller ?: run {
                        _state.update { it.copy(loading = false, error = "播放器还没准备好") }
                        return@launch
                    }
                    val item = MediaItem.Builder()
                        .setMediaId(ready.bvid)
                        .setUri(Uri.parse(url))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(ready.title)
                                .setArtist(ready.author)
                                .setArtworkUri(ready.cover.takeIf { it.isNotBlank() }?.let(Uri::parse))
                                .build(),
                        )
                        .build()
                    player.setMediaItem(item)
                    player.prepare()
                    player.play()
                    local.rememberPlay(ready)
                    failedInRow = 0
                    _state.update {
                        it.copy(index = index, track = ready, loading = false, playing = true)
                    }
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "play", e)
                    if (my != token) return@launch
                    val left = RETRY_MS - (SystemClock.elapsedRealtime() - failStamp)
                    if (left <= 0) {
                        skipBroken(index)
                        return@launch
                    }
                    delay(minOf(800L, left))
                }
            }
        }
    }

    private fun onPlayFailed(index: Int) {
        val track = _state.value.queue.getOrNull(index) ?: return
        if (failBvid != track.bvid) {
            failBvid = track.bvid
            failStamp = SystemClock.elapsedRealtime()
        }
        val my = token
        failJob?.cancel()
        failJob = scope.launch {
            val left = RETRY_MS - (SystemClock.elapsedRealtime() - failStamp)
            if (my != token) return@launch
            if (left <= 0) {
                skipBroken(index)
                return@launch
            }
            _state.update { it.copy(loading = true, playing = false, error = null) }
            delay(minOf(800L, left))
            if (my != token) return@launch
            start(index)
        }
    }

    private fun skipBroken(index: Int) {
        val snapshot = _state.value
        val queue = snapshot.queue
        failedInRow++
        val next = when (snapshot.mode) {
            PlayMode.ORDER -> index + 1
            PlayMode.SHUFFLE -> randomIndex(queue.size, index)
            else -> floorMod(index + 1, queue.size)
        }
        if (queue.size <= 1 || failedInRow >= queue.size || next !in queue.indices) {
            failedInRow = 0
            controller?.stop()
            _state.update { it.copy(loading = false, playing = false, error = "播放失败") }
            return
        }
        start(next)
    }

    private fun bind(player: MediaController) {
        controller = player
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = publishPlayback()

            override fun onPlaybackStateChanged(playbackState: Int) {
                publishPlayback()
                if (playbackState == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "exo", error)
                ticker?.cancel()
                onPlayFailed(_state.value.index)
            }
        })
        publishPlayback()
    }

    private fun publishPlayback() {
        val player = controller ?: return
        val playing = player.isPlaying
        if (_state.value.playing != playing) {
            _state.update { it.copy(playing = playing) }
        }
        publishProgress()
        syncTicker()
    }

    private fun syncTicker() {
        val run = uiActive && controller?.isPlaying == true
        if (!run) {
            ticker?.cancel()
            ticker = null
            publishProgress()
            return
        }
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                publishProgress()
                delay(500)
            }
        }
    }

    private fun publishProgress() {
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0 }
            ?: (_state.value.track?.durationSec?.times(1000L) ?: 0L)
        val next = PlayProgress(player.currentPosition.coerceAtLeast(0), duration)
        if (_progress.value != next) _progress.value = next
    }

    private fun onEnded() {
        if (_state.value.loading) return
        val snapshot = _state.value
        when (snapshot.mode) {
            PlayMode.ONE -> controller?.let {
                it.seekTo(0)
                it.play()
            }
            PlayMode.ORDER -> {
                if (snapshot.index + 1 < snapshot.queue.size) start(snapshot.index + 1)
            }
            PlayMode.LOOP, PlayMode.SHUFFLE -> move(1)
        }
    }

    private fun randomIndex(size: Int, current: Int): Int {
        if (size <= 1) return 0
        var next = current
        repeat(6) {
            next = Random.nextInt(size)
            if (next != current) return next
        }
        return (current + 1) % size
    }

    private fun floorMod(value: Int, size: Int): Int {
        if (size <= 0) return 0
        val mod = value % size
        return if (mod < 0) mod + size else mod
    }

    companion object {
        private const val TAG = "Busic"
        private const val RETRY_MS = 5_000L
    }
}
