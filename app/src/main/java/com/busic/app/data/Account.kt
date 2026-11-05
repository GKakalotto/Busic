package com.busic.app.data

data class BiliUser(
    val mid: Long,
    val name: String,
    val face: String,
)

data class FavFolder(
    val id: Long,
    val title: String,
    val count: Int,
    val cover: String,
)

data class FavPage(
    val tracks: List<Track>,
    val hasMore: Boolean,
)

data class QrTicket(
    val url: String,
    val key: String,
)

enum class QrState { Waiting, Scanned, Expired, Success }
