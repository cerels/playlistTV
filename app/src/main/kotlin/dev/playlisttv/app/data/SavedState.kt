package dev.playlisttv.app.data

import kotlinx.serialization.Serializable

/**
 * Everything saved between runs. Ported from the Rust prototype's `store.rs`,
 * adapted from a mutated struct to an immutable data class with pure
 * transform functions — the natural shape for a StateFlow in a ViewModel.
 */

/** Guide defaults (settings/js/epg.js:539-541). */
const val DEFAULT_KEEP_PAST_DAYS = 1L
const val DEFAULT_FUTURE_DAYS = 2L
const val DEFAULT_GUIDE_INTERVAL_H = 3L

/** Recently watched (groups/js/groups.js:172, :185). */
const val DEFAULT_RECENT_MAX = 10
const val RECENT_MAX_LIMIT = 50
val GUIDE_INTERVAL_CHOICES = listOf(2L, 3L, 6L, 12L, 24L)

@Serializable
data class PlaylistCfg(
    val id: Long,
    val name: String,
    /** URL or local file path. */
    val source: String,
    /** XMLTV guide URL or path; empty = use the one the playlist names. */
    val epgUrl: String = "",
    val epgShiftHours: Double = 0.0,
    val refreshed: Long = 0,
    val epgRefreshed: Long = 0,
    /** Guide URL found in the playlist header. */
    val headerEpg: String = "",
) {
    val guideSource: String get() = epgUrl.trim().ifEmpty { headerEpg.trim() }
}

@Serializable
data class Settings(
    /** Empty = find a player automatically. */
    val playerCmd: String = "",
    val keepPastDays: Long = DEFAULT_KEEP_PAST_DAYS,
    val futureDays: Long = DEFAULT_FUTURE_DAYS,
    val guideIntervalH: Long = DEFAULT_GUIDE_INTERVAL_H,
    val recentMax: Int = DEFAULT_RECENT_MAX,
    val showHidden: Boolean = false,
    val refreshOnStart: Boolean = false,
)

@Serializable
data class SavedState(
    val playlists: List<PlaylistCfg> = emptyList(),
    /** Favourite channel keys (name|tvg-id|tvg-name), in the order they were added. */
    val favourites: List<String> = emptyList(),
    /** Hidden categories per playlist id. */
    val hiddenGroups: Map<Long, Set<String>> = emptyMap(),
    /** Recently watched keys per playlist id, newest first. */
    val recent: Map<Long, List<String>> = emptyMap(),
    val settings: Settings = Settings(),
    val lastPlaylist: Long? = null,
    val nextId: Long = 0,
    val firstRunDone: Boolean = false,
) {
    fun isFav(key: String): Boolean = favourites.contains(key)

    /** Returns the new state; the channel's new favourite status is `isFav(key)` on the result. */
    fun toggleFav(key: String): SavedState =
        if (favourites.contains(key)) copy(favourites = favourites - key) else copy(favourites = favourites + key)

    fun isHidden(playlistId: Long, group: String): Boolean = hiddenGroups[playlistId]?.contains(group) == true

    fun setHidden(playlistId: Long, group: String, hidden: Boolean): SavedState {
        val current = hiddenGroups[playlistId] ?: emptySet()
        val updated = if (hidden) current + group else current - group
        return copy(hiddenGroups = hiddenGroups + (playlistId to updated))
    }

    fun pushRecent(playlistId: Long, key: String): SavedState {
        val max = settings.recentMax.coerceIn(1, RECENT_MAX_LIMIT)
        val current = recent[playlistId] ?: emptyList()
        val updated = (listOf(key) + current.filter { it != key }).take(max)
        return copy(recent = recent + (playlistId to updated))
    }

    /** Allocates the next playlist id; pair the returned id with the updated state. */
    fun allocatePlaylistId(): Pair<Long, SavedState> = nextId to copy(nextId = nextId + 1)
}
