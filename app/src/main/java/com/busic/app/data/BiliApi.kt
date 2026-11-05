package com.busic.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import android.content.Context
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class BiliException(message: String) : Exception(message)

data class SearchPage(
    val tracks: List<Track>,
    val numPages: Int,
)

class BiliApi(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jar = PersistCookieJar(context.applicationContext)
    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.bilibili.com")
                .build()
            chain.proceed(req)
        }
        .build()

    init {
        CoroutineScope(SupervisorJob() + Workers.dispatcher).launch {
            runCatching { ensureWbi() }
        }
    }

    private val lock = Any()
    private var mixinKey: String? = null
    private var wbiAt = 0L
    fun hasSession(): Boolean = jar.has("SESSDATA")

    fun logout() = jar.clear()

    fun adoptWebCookies(header: String) {
        val url = "https://www.bilibili.com/".toHttpUrl()
        val cookies = header.split(';').mapNotNull { piece ->
            val trimmed = piece.trim()
            val eq = trimmed.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val name = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            if (name !in WEB_COOKIES || value.isEmpty()) return@mapNotNull null
            runCatching {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain("bilibili.com")
                    .path("/")
                    .secure()
                    .expiresAt(System.currentTimeMillis() + 15552000000L)
                    .build()
            }.getOrNull()
        }
        if (cookies.isNotEmpty()) jar.saveFromResponse(url, cookies)
    }

    suspend fun nav(): BiliUser? = io {
        val data = get("$API/x/web-interface/nav", emptyMap(), sign = false).obj("data")
        if (!data.bool("isLogin")) return@io null
        val mid = data.long("mid")
        if (mid <= 0) return@io null
        BiliUser(mid, data.str("uname"), data.str("face").asCover())
    }

    suspend fun qrTicket(): QrTicket = io {
        val data = get(
            "$PASSPORT/x/passport-login/web/qrcode/generate",
            mapOf("source" to "main-fe-header"),
            sign = false,
        ).let { root ->
            requireOk(root)
            root.obj("data")
        }
        val url = data.str("url")
        val key = data.str("qrcode_key")
        if (url.isBlank() || key.isBlank()) throw BiliException("二维码获取失败")
        QrTicket(url, key)
    }

    suspend fun qrPoll(key: String): QrState = io {
        val root = get(
            "$PASSPORT/x/passport-login/web/qrcode/poll",
            mapOf("qrcode_key" to key, "source" to "main-fe-header"),
            sign = false,
        )
        requireOk(root)
        when (root.obj("data").int("code")) {
            0 -> QrState.Success
            86090, 86102 -> QrState.Scanned
            86038 -> QrState.Expired
            else -> QrState.Waiting
        }
    }

    suspend fun folders(mid: Long): List<FavFolder> = io {
        val root = get(
            "$API/x/v3/fav/folder/created/list-all",
            mapOf("up_mid" to mid.toString()),
            sign = false,
        )
        requireOk(root)
        root.obj("data").arr("list").mapNotNull { item ->
            val id = item.long("id")
            if (id <= 0) return@mapNotNull null
            FavFolder(
                id = id,
                title = item.str("title").ifBlank { "收藏夹" },
                count = item.int("media_count"),
                cover = "",
            )
        }
    }

    suspend fun folderCover(mediaId: Long): String = io {
        val root = get(
            "$API/x/v3/fav/resource/list",
            mapOf(
                "media_id" to mediaId.toString(),
                "pn" to "1",
                "ps" to "1",
                "platform" to "web",
            ),
            sign = false,
        )
        requireOk(root)
        root.obj("data").arr("medias").firstNotNullOfOrNull { it.toFavTrack()?.cover }.orEmpty()
    }

    suspend fun folderMedias(mediaId: Long, page: Int): FavPage = io {
        val root = get(
            "$API/x/v3/fav/resource/list",
            mapOf(
                "media_id" to mediaId.toString(),
                "pn" to page.toString(),
                "ps" to "20",
                "platform" to "web",
            ),
            sign = false,
        )
        requireOk(root)
        val data = root.obj("data")
        val tracks = data.arr("medias").mapNotNull { it.toFavTrack() }
        FavPage(tracks, tracks.size >= 20)
    }

    suspend fun search(keyword: String, page: Int = 1): SearchPage = io {
        val root = get(
            "$API/x/web-interface/wbi/search/type",
            mapOf(
                "keyword" to keyword,
                "search_type" to "video",
                "page" to page.toString(),
            ),
            sign = true,
        )
        requireOk(root)
        val data = root.obj("data")
        if (data.str("v_voucher").isNotBlank() && data["result"] == null) {
            throw BiliException("搜索被拦截了，请稍后再试")
        }
        val tracks = data.arr("result").mapNotNull { it.toSearchTrack() }
        val pages = data.int("numPages").takeIf { it > 0 }
            ?: if (tracks.size >= 20) page + 1 else page
        SearchPage(tracks, pages)
    }

    suspend fun audioUrl(track: Track): Pair<Track, String> = io {
        val ready = if (track.cid > 0) track else {
            val root = get(
                "$API/x/web-interface/view",
                mapOf("bvid" to track.bvid),
                sign = false,
            )
            requireOk(root)
            val data = root.obj("data")
            track.copy(
                aid = data.long("aid").takeIf { it > 0 } ?: track.aid,
                cid = data.long("cid"),
                title = data.str("title").plainTitle().ifBlank { track.title },
                author = data.obj("owner").str("name").ifBlank { track.author },
                cover = data.str("pic").asCover().ifBlank { track.cover },
                durationSec = data.int("duration").takeIf { it > 0 } ?: track.durationSec,
            )
        }
        if (ready.cid <= 0) throw BiliException("没有分P信息")
        val play = get(
            "$API/x/player/wbi/playurl",
            mapOf(
                "bvid" to ready.bvid,
                "cid" to ready.cid.toString(),
                "qn" to "0",
                "fnval" to "16",
                "fnver" to "0",
                "fourk" to "1",
            ),
            sign = true,
        )
        requireOk(play)
        val audios = play.obj("data").obj("dash").arr("audio")
        val best = audios.maxByOrNull { it.int("bandwidth") }
            ?: throw BiliException("没有音频流")
        val url = best.str("baseUrl").ifBlank { best.str("base_url") }
        if (url.isBlank()) throw BiliException("音频地址为空")
        ready to url
    }

    private fun requireOk(root: JsonObject) {
        val code = root.int("code")
        if (code != 0) {
            throw BiliException(root.str("message").ifBlank { "请求失败 $code" })
        }
    }

    private fun get(base: String, params: Map<String, String>, sign: Boolean): JsonObject {
        if (sign) ensureWbi()
        val query = if (sign) signedQuery(params) else plainQuery(params)
        val url = if (query.isEmpty()) base else "$base?$query"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw BiliException("网络错误 ${resp.code}")
            return json.parseToJsonElement(text).jsonObject
        }
    }

    private fun ensureWbi() {
        synchronized(lock) {
            val cached = mixinKey
            val fresh = cached != null && System.currentTimeMillis() - wbiAt < 12 * 60 * 60 * 1000
            if (fresh) return
            val nav = get("$API/x/web-interface/nav", emptyMap(), sign = false)
            val img = nav.obj("data").obj("wbi_img")
            val raw = img.str("img_url").wbiKey() + img.str("sub_url").wbiKey()
            if (raw.length < 64) throw BiliException("拿不到签名密钥")
            mixinKey = MIXIN.map { raw[it] }.joinToString("").take(32)
            wbiAt = System.currentTimeMillis()
        }
    }

    private fun signedQuery(params: Map<String, String>): String {
        val key = mixinKey ?: throw BiliException("签名未就绪")
        val merged = params + ("wts" to (System.currentTimeMillis() / 1000).toString())
        val filtered = merged.mapValues { (_, v) -> v.replace(Regex("[!'()*]"), "") }
        val query = filtered.keys.sorted().joinToString("&") { k ->
            "${enc(k)}=${enc(filtered.getValue(k))}"
        }
        val rid = md5(query + key)
        return "$query&w_rid=$rid"
    }

    private fun plainQuery(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun JsonObject.toFavTrack(): Track? {
        val bvid = str("bvid")
        if (bvid.isBlank()) return null
        return Track(
            bvid = bvid,
            aid = long("id"),
            cid = 0,
            title = str("title").plainTitle(),
            author = obj("upper").str("name"),
            cover = str("cover").asCover(),
            durationSec = int("duration"),
        )
    }

    private fun JsonObject.toSearchTrack(): Track? {
        val bvid = str("bvid")
        if (bvid.isBlank()) return null
        return Track(
            bvid = bvid,
            aid = long("aid"),
            cid = 0,
            title = str("title").plainTitle(),
            author = str("author"),
            cover = str("pic").asCover(),
            durationSec = durationOf(this["duration"]),
        )
    }

    private fun durationOf(value: kotlinx.serialization.json.JsonElement?): Int {
        val text = (value as? JsonPrimitive)?.contentOrNull ?: return 0
        if (':' !in text) return text.toIntOrNull() ?: 0
        val parts = text.split(':').map { it.toIntOrNull() ?: return 0 }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> 0
        }
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.long(key: String): Long =
        (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.contentOrNull == "true"

    private fun JsonObject.obj(key: String): JsonObject =
        this[key] as? JsonObject ?: JsonObject(emptyMap<String, JsonElement>())

    private fun JsonObject.arr(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private suspend fun <T> io(block: () -> T): T =
        kotlinx.coroutines.withContext(Workers.dispatcher) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            }
        }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val WEB_COOKIES = setOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")
        private const val API = "https://api.bilibili.com"
        private const val PASSPORT = "https://passport.bilibili.com"
        private val MIXIN = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 16,
            20, 36, 34, 17, 6, 22, 48, 44, 13, 52, 37, 4, 40, 25, 51, 11,
            55, 7, 24, 1, 21, 56, 57, 30, 54, 26, 0, 59, 60, 61, 62, 63,
        )

        private fun enc(raw: String): String = buildString {
            for (byte in raw.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt() and 0xFF
                val keep = c in 'A'.code..'Z'.code ||
                    c in 'a'.code..'z'.code ||
                    c in '0'.code..'9'.code ||
                    c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
                if (keep) append(c.toChar()) else append("%").append("%02X".format(c))
            }
        }

        private fun md5(text: String): String {
            val dig = MessageDigest.getInstance("MD5").digest(text.toByteArray())
            return dig.joinToString("") { "%02x".format(it) }
        }

        private fun String.wbiKey(): String = substringAfterLast('/').substringBefore('.')
    }
}

