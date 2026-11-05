package com.busic.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("busic_local", Context.MODE_PRIVATE)
    private val _recent = MutableStateFlow(readTracks())
    val recent: StateFlow<List<Track>> = _recent
    private val _queries = MutableStateFlow(readQueries())
    val queries: StateFlow<List<String>> = _queries

    fun recentFolder(tracks: List<Track> = _recent.value): FavFolder = FavFolder(
        id = RECENT_ID,
        title = "最近播放",
        count = tracks.size,
        cover = tracks.firstOrNull()?.cover.orEmpty(),
    )

    fun rememberPlay(track: Track) {
        if (track.bvid.isBlank()) return
        val next = (listOf(track) + _recent.value.filter { it.bvid != track.bvid }).take(50)
        _recent.value = next
        prefs.edit().putString(KEY_PLAYS, writeTracks(next)).apply()
    }

    fun clearPlays() {
        _recent.value = emptyList()
        prefs.edit().remove(KEY_PLAYS).apply()
    }

    fun rememberQuery(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        val next = (listOf(query) + _queries.value.filter { it != query }).take(12)
        _queries.value = next
        prefs.edit().putString(KEY_QUERIES, JSONArray(next).toString()).apply()
    }

    fun clearQueries() {
        _queries.value = emptyList()
        prefs.edit().remove(KEY_QUERIES).apply()
    }

    private fun readTracks(): List<Track> {
        val raw = prefs.getString(KEY_PLAYS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        Track(
                            bvid = item.optString("bvid"),
                            aid = item.optLong("aid"),
                            cid = item.optLong("cid"),
                            title = item.optString("title"),
                            author = item.optString("author"),
                            cover = item.optString("cover"),
                            durationSec = item.optInt("durationSec"),
                        ),
                    )
                }
            }.filter { it.bvid.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun writeTracks(tracks: List<Track>): String {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(
                JSONObject()
                    .put("bvid", track.bvid)
                    .put("aid", track.aid)
                    .put("cid", track.cid)
                    .put("title", track.title)
                    .put("author", track.author)
                    .put("cover", track.cover)
                    .put("durationSec", track.durationSec),
            )
        }
        return array.toString()
    }

    private fun readQueries(): List<String> {
        val raw = prefs.getString(KEY_QUERIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val text = array.optString(i).trim()
                    if (text.isNotEmpty()) add(text)
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val RECENT_ID = -1L
        private const val KEY_PLAYS = "plays"
        private const val KEY_QUERIES = "queries"
    }
}
