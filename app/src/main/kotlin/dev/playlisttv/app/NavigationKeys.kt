package dev.playlisttv.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

/** Named `Playback`, not `Player`, to avoid clashing with media3's `Player` interface. */
@Serializable
data class Playback(
    val name: String,
    val url: String,
    val userAgent: String,
    val referrer: String,
    val headers: String,
) : NavKey
