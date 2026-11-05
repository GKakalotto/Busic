package com.busic.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("busic_cookies", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val store = mutableListOf<Cookie>()

    init {
        val raw = prefs.getString(KEY, null)
        if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<StoredCookie>>(raw) }
                .getOrNull()
                ?.mapNotNull { it.toCookie() }
                ?.let { store.addAll(it) }
        }
    }

    fun has(name: String): Boolean = synchronized(store) {
        store.any { it.name == name && it.expiresAt > System.currentTimeMillis() }
    }

    fun clear() {
        synchronized(store) {
            store.clear()
            prefs.edit().remove(KEY).apply()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(store) {
            cookies.forEach { incoming ->
                store.removeAll { it.name == incoming.name && it.domain == incoming.domain }
                if (incoming.expiresAt > System.currentTimeMillis()) store.add(incoming)
            }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(store) {
        val now = System.currentTimeMillis()
        if (store.removeAll { it.expiresAt <= now }) persist()
        store.filter { it.matches(url) }
    }

    private fun persist() {
        val raw = json.encodeToString(store.map { it.stored() })
        prefs.edit().putString(KEY, raw).apply()
    }

    private fun Cookie.stored() = StoredCookie(
        name = name,
        value = value,
        expiresAt = expiresAt,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        hostOnly = hostOnly,
    )

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
    ) {
        fun toCookie(): Cookie? = runCatching {
            val builder = Cookie.Builder().name(name).value(value).path(path)
            if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()
            if (expiresAt < Long.MAX_VALUE / 2) builder.expiresAt(expiresAt)
            builder.build()
        }.getOrNull()
    }

    private companion object {
        const val KEY = "jar"
    }
}
