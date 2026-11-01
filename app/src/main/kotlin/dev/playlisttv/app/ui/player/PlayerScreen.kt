package dev.playlisttv.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import dev.playlisttv.app.Playback
import dev.playlisttv.app.ui.theme.BgBase
import dev.playlisttv.app.ui.theme.White
import androidx.tv.material3.Text

/**
 * In-app full-screen playback via ExoPlayer/`PlayerView`. The Rust desktop prototype shelled
 * out to an external player (MECHANICS.md "Estimated / chosen") because that's the natural fit
 * for a desktop app with no bundled player UI of its own — on Android TV there's no guarantee
 * any external player is installed, and in-app playback is the idiomatic, Leanback-expected
 * experience (the system remote drives it directly), so that choice doesn't carry over.
 */
@Composable
fun PlayerScreen(playback: Playback, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var errorMessage by remember(playback) { mutableStateOf<String?>(null) }
    var isBuffering by remember(playback) { mutableStateOf(true) }

    val exoPlayer = remember(playback) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(playback.userAgent.ifBlank { null })
            .setDefaultRequestProperties(requestHeaders(playback))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(playback.url))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Media3Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Media3Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.message ?: "Playback failed"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    BackHandler(onBack = onBack)

    Box(modifier = modifier.fillMaxSize().background(BgBase)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            update = { it.player = exoPlayer },
        )
        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        errorMessage?.let { message ->
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Couldn't play ${playback.name}: $message", color = White)
            }
        }
    }
}

/** Referrer plus the raw KODIPROP `stream_headers` (`key=value&key=value...`), as `Channel` carries them. */
private fun requestHeaders(playback: Playback): Map<String, String> {
    val headers = mutableMapOf<String, String>()
    if (playback.referrer.isNotBlank()) headers["Referer"] = playback.referrer
    for (pair in playback.headers.split("&")) {
        val eq = pair.indexOf('=')
        if (eq <= 0) continue
        val key = pair.substring(0, eq).trim()
        val value = pair.substring(eq + 1).trim()
        if (key.isNotEmpty()) headers[key] = value
    }
    return headers
}
