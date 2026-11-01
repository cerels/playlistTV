package dev.playlisttv.app.data

/**
 * M3U / M3U8 playlist reader. Rules follow MECHANICS.md ("Playlist parsing"),
 * ported from the Rust prototype's `m3u.rs` (clean-room re-implementation of
 * js/playlist-worker.js:357, see MECHANICS.md).
 */

enum class Kind { LIVE, MOVIE, SERIES }

data class Channel(
    val name: String,
    val group: String,
    /** Kept for a future logo view; tiles use initials for now. */
    val logo: String,
    val tvgId: String,
    val tvgName: String,
    val chno: Double?,
    val url: String,
    val userAgent: String,
    val referrer: String,
    val headers: String,
    val kind: Kind,
    /** Position in the playlist, from 1. */
    val pos: Int,
) {
    /** Favourite key, same shape as the original: name|tvg-id|tvg-name. */
    val key: String get() = "$name|$tvgId|$tvgName"
}

data class ParsedPlaylist(
    val channels: List<Channel>,
    val epgUrl: String?,
    val hasHeader: Boolean,
)

private fun Char.isAsciiAlnum(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

/**
 * Read `key="value"` from an #EXTINF line. The key must start a word, so
 * `type` doesn't match inside `catchup-type`.
 */
fun attr(line: String, key: String): String? {
    val pat = "$key=\""
    var from = 0
    while (true) {
        val i = line.indexOf(pat, from)
        if (i == -1) return null
        val ok = i == 0 || run {
            val c = line[i - 1]
            !(c.isAsciiAlnum() || c == '-' || c == '_')
        }
        val vstart = i + pat.length
        if (ok) {
            val end = line.indexOf('"', vstart)
            if (end == -1) return null
            val v = line.substring(vstart, end)
            return v.ifEmpty { null }
        }
        from = vstart
    }
}

private class Building(
    var name: String,
    var group: String,
    val logo: String,
    val tvgId: String,
    val tvgName: String,
    val chno: Double?,
    var userAgent: String,
    var referrer: String,
    var headers: String,
    var kind: Kind,
)

fun parsePlaylist(content: String): ParsedPlaylist {
    var hasHeader = false
    var epgUrl: String? = null
    val channels = mutableListOf<Channel>()
    var cur: Building? = null
    var pendingGroup: String? = null
    var pendingUa = ""
    var pendingRef = ""
    var pendingHeaders = ""

    val body = content.removePrefix("\uFEFF")
    for (raw in body.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        when {
            line.startsWith("#EXTM3U") -> {
                hasHeader = true
                for (k in listOf("url-tvg", "x-tvg-url", "epg-url", "tvg-url")) {
                    val u = attr(line, k)
                    if (u != null) {
                        epgUrl = u.substringBefore(',').trim()
                        break
                    }
                }
            }
            line.startsWith("#EXTVLCOPT:") -> {
                val v = line.removePrefix("#EXTVLCOPT:")
                when {
                    v.startsWith("http-referrer=") -> pendingRef = v.removePrefix("http-referrer=")
                    v.startsWith("http-referer=") -> pendingRef = v.removePrefix("http-referer=")
                    v.startsWith("http-user-agent=") -> pendingUa = v.removePrefix("http-user-agent=")
                }
            }
            line.startsWith("#KODIPROP:inputstream.adaptive.stream_headers=") -> {
                val v = line.removePrefix("#KODIPROP:inputstream.adaptive.stream_headers=")
                when {
                    v.startsWith("referer=") -> pendingRef = v.removePrefix("referer=")
                    v.startsWith("user-agent=") -> pendingUa = v.removePrefix("user-agent=")
                    else -> pendingHeaders = v
                }
            }
            line.startsWith("#EXTGRP:") -> {
                val g = line.removePrefix("#EXTGRP:").trim()
                val c = cur
                if (c != null && c.group == "-") {
                    c.group = g
                } else {
                    pendingGroup = g
                }
            }
            line.startsWith("#EXTINF:") -> {
                var name: String
                val qi = line.indexOf("\",")
                name = if (qi != -1 && qi + 2 < line.length) {
                    line.substring(qi + 2).trim()
                } else {
                    val ci = line.indexOf(',')
                    if (ci != -1) line.substring(ci + 1).trim() else ""
                }
                var group = attr(line, "group-title") ?: attr(line, "tvg-group")
                if (group == null && name.indexOf(" | ") == 2) {
                    group = name.substring(0, 3).trim()
                    name = name.substring(4).trim()
                }
                if (group == null) {
                    group = pendingGroup
                    pendingGroup = null
                }
                val finalGroup = group ?: "-"
                val typ = attr(line, "type") ?: ""
                val tvgId = attr(line, "tvg-id") ?: attr(line, "channel-id") ?: name
                val chno = (attr(line, "tvg-chno") ?: attr(line, "ch-number"))?.trim()?.toDoubleOrNull()
                val serie = attr(line, "tvg-serie") != null || attr(line, "tvg-season") != null
                val kind = when {
                    finalGroup == "Movies" || typ == "movie" -> Kind.MOVIE
                    finalGroup == "Serie" || typ == "series" || serie -> Kind.SERIES
                    else -> Kind.LIVE
                }
                cur = Building(
                    name = name,
                    group = finalGroup,
                    logo = attr(line, "tvg-logo") ?: "",
                    tvgId = tvgId,
                    tvgName = attr(line, "tvg-name") ?: "",
                    chno = chno,
                    userAgent = pendingUa,
                    referrer = pendingRef,
                    headers = pendingHeaders,
                    kind = kind,
                )
                pendingUa = ""
                pendingRef = ""
                pendingHeaders = ""
            }
            !line.startsWith("#") -> {
                val c = cur
                cur = null
                if (c != null) {
                    var url = line
                    if (pendingUa.isNotEmpty()) {
                        c.userAgent = pendingUa
                        pendingUa = ""
                    }
                    if (pendingRef.isNotEmpty()) {
                        c.referrer = pendingRef
                        pendingRef = ""
                    }
                    if (c.headers.isEmpty()) {
                        val pipeIdx = url.indexOf('|')
                        if (pipeIdx > 10 && url.count { it == '|' } == 1) {
                            c.headers = url.substring(pipeIdx + 1)
                            url = url.substring(0, pipeIdx)
                        }
                    }
                    if (c.kind == Kind.LIVE) {
                        val mi = url.indexOf("/movie")
                        val si = url.indexOf("/serie")
                        if (mi > 2) {
                            c.kind = Kind.MOVIE
                        } else if (si > 2) {
                            c.kind = Kind.SERIES
                        }
                    }
                    if (c.name.isNotEmpty()) {
                        channels.add(
                            Channel(
                                name = c.name,
                                group = c.group,
                                logo = c.logo,
                                tvgId = c.tvgId,
                                tvgName = c.tvgName,
                                chno = c.chno,
                                url = url,
                                userAgent = c.userAgent,
                                referrer = c.referrer,
                                headers = c.headers,
                                kind = c.kind,
                                pos = 0,
                            ),
                        )
                    }
                }
            }
        }
    }

    // Playlists that give channel numbers keep that order; the rest stay in file order.
    val ordered = if (channels.any { it.chno != null }) {
        channels.sortedWith(compareBy(nullsLast()) { it.chno })
    } else {
        channels
    }
    return ParsedPlaylist(
        channels = ordered.mapIndexed { i, c -> c.copy(pos = i + 1) },
        epgUrl = epgUrl,
        hasHeader = hasHeader,
    )
}

/** Turn share links into direct downloads (Dropbox raw=1, Google Drive file links). */
fun fixUrl(input: String): String {
    val url = input.trim()
    if (url.contains("dropbox.com") && !url.contains("raw=1")) {
        return if (url.contains('?')) "$url&raw=1" else "$url?raw=1"
    }
    val prefix = "https://drive.google.com/file/d/"
    if (url.startsWith(prefix)) {
        val id = url.removePrefix(prefix).substringBefore('/')
        return "https://drive.usercontent.google.com/download?id=$id&export=download&authuser=0"
    }
    return url
}
