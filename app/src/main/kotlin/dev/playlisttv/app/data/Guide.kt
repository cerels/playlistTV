package dev.playlisttv.app.data

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * XMLTV guide reader. Rules follow MECHANICS.md ("Guide (XMLTV)"), ported
 * from the Rust prototype's `epg.rs` (clean-room re-implementation of
 * js/epg-worker.js, see MECHANICS.md).
 */

/** Programmes shorter than this are dropped (js/epg-worker.js:227). */
const val SHORTEST_PROGRAMME_MS = 240_000L

/** Descriptions shorter than this are ignored (js/epg-worker.js:718). */
const val SHORTEST_DESC_CHARS = 7

data class Programme(
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String,
    val category: String,
)

class Guide {
    /** XMLTV channel id -> programmes sorted by start. */
    val programmes: MutableMap<String, MutableList<Programme>> = mutableMapOf()

    /** Channel id and every display name -> XMLTV channel id. */
    val names: MutableMap<String, String> = mutableMapOf()

    /** Loose name (see [loose]) -> XMLTV channel id, used when no exact name matches. */
    val looseNames: MutableMap<String, String> = mutableMapOf()

    /** XMLTV channel id -> icon URL. */
    val icons: MutableMap<String, String> = mutableMapOf()

    /** Find the XMLTV channel for a playlist channel: tvg-id, then name, then tvg-name. */
    fun channelId(tvgId: String, name: String, tvgName: String): String? {
        val keys = listOf(tvgId, name, tvgName)
        for (k in keys) {
            if (k.isEmpty()) continue
            val id = names[k]
            if (id != null && programmes.containsKey(id)) return id
        }
        for (k in keys) {
            val l = loose(k)
            if (l.isEmpty()) continue
            val id = looseNames[l]
            if (id != null && programmes.containsKey(id)) return id
        }
        return null
    }
}

data class GuideWindow(val keepPastDays: Long, val futureDays: Long, val shiftHours: Double, val now: Long)

private val QUALITY_WORDS = setOf("hd", "sd", "fhd", "uhd", "4k", "hevc", "480p", "576p", "720p", "1080p", "2160p")

/**
 * A forgiving form of a channel name or id for matching playlists to guides:
 * lower case, no "(1080p)" / "[Geo-blocked]" notes, nothing after "@", no
 * quality words, letters and digits only. So "Canal Capital (1080p)",
 * "CanalCapital.co@SD" and the guide's "Canal.Capital.co" all line up.
 * (The original only matches exact names; this is an addition.)
 */
fun loose(s: String): String {
    val base = s.substringBefore('@').lowercase()
    val kept = StringBuilder()
    var depth = 0
    for (ch in base) {
        when {
            ch == '(' || ch == '[' -> depth += 1
            ch == ')' || ch == ']' -> depth = (depth - 1).coerceAtLeast(0)
            depth == 0 -> kept.append(ch)
        }
    }
    val words = mutableListOf<String>()
    val word = StringBuilder()
    for (ch in kept) {
        if (ch.isLetterOrDigit()) {
            word.append(ch)
        } else if (word.isNotEmpty()) {
            words.add(word.toString())
            word.clear()
        }
    }
    if (word.isNotEmpty()) words.add(word.toString())
    return words.filter { it !in QUALITY_WORDS }.joinToString("")
}

fun ungzipIfNeeded(bytes: ByteArray): ByteArray {
    if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
        return try {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        } catch (e: IOException) {
            bytes
        }
    }
    return bytes
}

private class ProgBuilder(val start: Long, val stop: Long) {
    val title = StringBuilder()
    val desc = StringBuilder()
    val category = StringBuilder()
}

/**
 * Parse a guide. `wanted` holds every name, tvg-id and tvg-name of the loaded
 * playlists; only programmes of matching XMLTV channels are kept.
 */
fun parseGuide(xml: ByteArray, wanted: Set<String>, window: GuideWindow): Guide {
    val guide = Guide()
    val wantedLoose = wanted.map { loose(it) }.filter { it.isNotEmpty() }.toSet()
    val past = if (window.keepPastDays > 0) window.now - window.keepPastDays * 86400 else window.now - 2 * 3600
    val ahead = window.now + maxOf(window.futureDays, 1) * 86400

    val parser: XmlPullParser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(ByteArrayInputStream(xml), null)

    val allowed = mutableSetOf<String>()
    var chId: String? = null
    var chNames = mutableListOf<String>()
    var chIcon: String? = null
    var progChannel: String? = null
    var prog: ProgBuilder? = null
    val text = StringBuilder()

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> {
                text.setLength(0)
                when (parser.name) {
                    "channel" -> if (prog == null) {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        chNames = mutableListOf(id)
                        chId = id
                        chIcon = null
                    }
                    "programme" -> {
                        val chan = parser.getAttributeValue(null, "channel") ?: ""
                        val start = parser.getAttributeValue(null, "start")?.let { Clock.parseXmltv(it, window.shiftHours) }
                        val stop = parser.getAttributeValue(null, "stop")?.let { Clock.parseXmltv(it, window.shiftHours) }
                        if (allowed.contains(chan) && start != null && stop != null &&
                            start <= ahead && stop >= past && (stop - start) * 1000 >= SHORTEST_PROGRAMME_MS
                        ) {
                            prog = ProgBuilder(start, stop)
                            progChannel = chan
                        } else {
                            prog = null
                            progChannel = null
                        }
                    }
                    "icon" -> {
                        if (chId != null && prog == null) {
                            chIcon = parser.getAttributeValue(null, "src")
                        }
                    }
                }
            }
            XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                text.append(parser.text)
            }
            XmlPullParser.END_TAG -> {
                val t = text.toString().trim()
                when (parser.name) {
                    "display-name" -> if (chId != null && t.isNotEmpty()) chNames.add(t)
                    "channel" -> {
                        val id = chId
                        if (id != null) {
                            val hit = chNames.any { wanted.contains(it) || wantedLoose.contains(loose(it)) }
                            if (hit) {
                                allowed.add(id)
                                chIcon?.let { guide.icons[id] = it }
                            }
                            for (n in chNames) {
                                val l = loose(n)
                                if (l.isNotEmpty()) guide.looseNames.putIfAbsent(l, id)
                                guide.names.putIfAbsent(n, id)
                            }
                        }
                        chId = null
                        chNames = mutableListOf()
                    }
                    "programme" -> {
                        val p = prog
                        val chan = progChannel
                        if (p != null && chan != null) {
                            guide.programmes.getOrPut(chan) { mutableListOf() }
                                .add(Programme(p.start, p.stop, p.title.toString(), p.desc.toString(), p.category.toString()))
                        }
                        prog = null
                        progChannel = null
                    }
                    "title", "desc", "category", "sub-title" -> {
                        val p = prog
                        if (p != null && t.isNotEmpty()) {
                            val skip = parser.name == "desc" && t.length < SHORTEST_DESC_CHARS
                            if (!skip) {
                                val sb = when (parser.name) {
                                    "title" -> p.title
                                    "desc", "sub-title" -> p.desc
                                    else -> p.category
                                }
                                if (sb.isNotEmpty()) sb.append(" | ")
                                sb.append(t)
                            }
                        }
                    }
                }
                text.setLength(0)
            }
        }
        eventType = try {
            parser.next()
        } catch (e: Exception) {
            break
        }
    }

    for (list in guide.programmes.values) {
        list.sortBy { it.start }
        var i = 1
        while (i < list.size) {
            if (list[i].start == list[i - 1].start) list.removeAt(i) else i++
        }
    }
    return guide
}

/** Index of the programme on air at `t`, if any. */
fun onAir(list: List<Programme>, t: Long): Int? {
    var lo = 0
    var hi = list.size
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (list[mid].start <= t) lo = mid + 1 else hi = mid
    }
    if (lo == 0) return null
    return if (list[lo - 1].stop > t) lo - 1 else null
}
