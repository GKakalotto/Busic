package com.busic.app.data

data class Track(
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
    val author: String,
    val cover: String,
    val durationSec: Int,
)

fun String.asCover(): String = when {
    startsWith("//") -> "https:$this"
    startsWith("http://") -> "https://" + removePrefix("http://")
    else -> this
}

fun String.plainTitle(): String = replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .trim()

fun formatClock(totalSec: Int): String {
    if (totalSec <= 0) return ""
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatMmSs(totalSec: Int): String {
    if (totalSec <= 0) return ""
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun formatClockMs(ms: Long): String = formatClock((ms / 1000).toInt().coerceAtLeast(0))
