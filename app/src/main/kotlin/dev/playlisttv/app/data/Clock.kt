package dev.playlisttv.app.data

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Small time helpers. Ported from the Rust prototype's `clock.rs`, but using
 * java.time / the system's real IANA zone instead of a raw `date +%z` offset
 * hack — same end result (local wall-clock time), handled correctly across
 * DST transitions.
 */
object Clock {
    fun now(): Long = Instant.now().epochSecond

    /** "+0130" / "-0500" -> seconds. */
    fun parseOffset(s: String): Int? {
        if (s.length != 5 || (s[0] != '+' && s[0] != '-')) return null
        val h = s.substring(1, 3).toIntOrNull() ?: return null
        val m = s.substring(3, 5).toIntOrNull() ?: return null
        val v = (h * 60 + m) * 60
        return if (s[0] == '-') -v else v
    }

    /**
     * XMLTV time ("YYYYMMDDhhmmss" with optional " ±hhmm") to unix seconds.
     * No offset means +0000; `shiftHours` is added to the offset like the
     * original's per-source time shift (js/epg-worker.js:139-190).
     */
    fun parseXmltv(raw: String, shiftHours: Double = 0.0): Long? {
        val s = raw.trim()
        if (s.length < 14 || !s.substring(0, 14).all { it.isDigit() }) return null
        val y = s.substring(0, 4).toInt()
        val mo = s.substring(4, 6).toInt()
        val d = s.substring(6, 8).toInt()
        val h = s.substring(8, 10).toInt()
        val mi = s.substring(10, 12).toInt()
        val se = s.substring(12, 14).toInt()
        val rest = s.substring(14).trim()
        val offset = if (rest.isEmpty()) 0 else (parseOffset(rest) ?: return null)
        val shiftedOffset = offset + (shiftHours * 3600.0).roundToLong()
        val epochDay = try {
            LocalDate.of(y, mo, d).toEpochDay()
        } catch (e: DateTimeException) {
            return null
        }
        val local = epochDay * 86400 + h * 3600 + mi * 60 + se
        return local - shiftedOffset
    }

    fun hm(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochSecond(ts).atZone(zone)
        return "%02d:%02d".format(t.hour, t.minute)
    }

    fun dayLabel(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochSecond(ts).atZone(zone)
        val wd = t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        return "%s %02d.%02d".format(wd, t.dayOfMonth, t.monthValue)
    }

    /** Start of the local hour containing `ts`. */
    fun hourFloor(ts: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochSecond(ts).atZone(zone).truncatedTo(ChronoUnit.HOURS).toEpochSecond()
}
