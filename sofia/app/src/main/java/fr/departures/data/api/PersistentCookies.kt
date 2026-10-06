package fr.departures.data.api

import android.content.SharedPreferences
import androidx.core.content.edit
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.http.renderSetCookieHeader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Cookie jar that survives process death (the Sofia session lasts ~2 h; widgets restart the
 * process often). Matching/expiry is delegated to Ktor's in-memory storage; this class only
 * mirrors each cookie, keyed by name, into SharedPreferences.
 */
class PersistentCookiesStorage(private val prefs: SharedPreferences) : CookiesStorage {
    private var memory = AcceptAllCookiesStorage()
    private val lock = Mutex()
    private var loaded = false

    private suspend fun load() {
        if (loaded) return
        loaded = true
        val saved = prefs.getString(KEY, null) ?: return
        runCatching {
            val o = JSONObject(saved)
            for (name in o.keys()) {
                val e = o.getJSONObject(name)
                memory.addCookie(Url(e.getString("url")), parseServerSetCookieHeader(e.getString("h")))
            }
        }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> = lock.withLock {
        load()
        memory.get(requestUrl)
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = lock.withLock {
        load()
        memory.addCookie(requestUrl, cookie)
        val o = prefs.getString(KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        o.put(cookie.name, JSONObject().put("url", requestUrl.toString()).put("h", renderSetCookieHeader(cookie)))
        prefs.edit { putString(KEY, o.toString()) }
    }

    suspend fun clear() = lock.withLock {
        prefs.edit { remove(KEY) }
        memory.close()
        memory = AcceptAllCookiesStorage()
        loaded = true
    }

    override fun close() = memory.close()

    companion object { private const val KEY = "cookies" }
}
