package dev.playlisttv.app.data

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Loads/saves [SavedState] to disk, and downloads playlist/guide sources. */
object AppStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun load(dir: File): SavedState {
        val file = File(dir, "state.json")
        return runCatching { json.decodeFromString(SavedState.serializer(), file.readText()) }.getOrDefault(SavedState())
    }

    fun save(state: SavedState, dir: File) {
        dir.mkdirs()
        val tmp = File(dir, "state.json.tmp")
        runCatching {
            tmp.writeText(json.encodeToString(SavedState.serializer(), state))
            tmp.renameTo(File(dir, "state.json"))
        }
    }

    fun playlistCache(dir: File, id: Long): File = File(File(dir, "playlists"), "$id.m3u")

    fun guideCache(dir: File, id: Long): File = File(File(dir, "guides"), "$id.xml")

    /** Read a URL or local file fully. */
    suspend fun fetch(source: String): ByteArray = withContext(Dispatchers.IO) {
        val s = source.trim()
        if (s.startsWith("http://") || s.startsWith("https://")) {
            val request = Request.Builder()
                .url(fixUrl(s))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android TV) PlaylistTV/1.0")
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("The server answered with error ${resp.code}")
                resp.body?.bytes() ?: throw IOException("Empty response")
            }
        } else {
            File(s.removePrefix("file://")).readBytes()
        }
    }
}
