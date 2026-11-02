package dev.playlisttv.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.playlisttv.app.Playback
import dev.playlisttv.app.ui.theme.BgBase
import dev.playlisttv.app.ui.theme.BgSurfaceElevated
import dev.playlisttv.app.ui.theme.ThemeHighlight
import dev.playlisttv.app.ui.theme.White
import kotlinx.coroutines.delay
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text

private val ResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
)

/**
 * In-app full-screen playback via ExoPlayer/`PlayerView`. The Rust desktop prototype shelled
 * out to an external player (MECHANICS.md "Estimated / chosen") because that's the natural fit
 * for a desktop app with no bundled player UI of its own — on Android TV there's no guarantee
 * any external player is installed, and in-app playback is the idiomatic, Leanback-expected
 * experience (the system remote drives it directly), so that choice doesn't carry over.
 *
 * Up/Down tune the previous/next channel in place (no list is visible during playback, so a
 * transient label stands in for one); Left/Right cycle the picture's resize mode. Both are
 * intercepted here, in the tunneling phase, before [PlayerView]'s own embedded View can consume
 * them — the same pattern [dev.playlisttv.app.ui.components.AddPlaylistForm] uses for D-pad vs.
 * a text field, needed again here because an `AndroidView` dispatches key events itself.
 */
@Composable
fun PlayerScreen(
    playback: Playback,
    onBack: () -> Unit,
    onSwitchChannel: (delta: Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var errorMessage by remember(playback) { mutableStateOf<String?>(null) }
    var isBuffering by remember(playback) { mutableStateOf(true) }
    var retryToken by remember(playback) { mutableIntStateOf(0) }

    val exoPlayer = remember(playback, retryToken) {
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

    var resizeModeIndex by remember(playback) { mutableIntStateOf(0) }
    var hintLabel by remember(playback) { mutableStateOf<String?>(playback.name) }
    val tryAgainFocusRequester = remember { FocusRequester() }

    // PlayerView's own controller is focusable and otherwise steals both D-pad focus and
    // DPAD_CENTER from the "Try again"/"Back to channels" buttons drawn on top of it.
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) tryAgainFocusRequester.requestFocus()
    }

    // Restarts every time hintLabel is set to something new (including the same text twice in a
    // row, since LaunchedEffect's key is the state read, not object identity of the assignment).
    LaunchedEffect(hintLabel) {
        if (hintLabel == null) return@LaunchedEffect
        delay(2000)
        hintLabel = null
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgBase)
            .onPreviewKeyEvent { event ->
                // Only hijack D-pad for tuning/resize during normal playback: with an error shown,
                // Left/Right must reach normal focus traversal instead, or "Back to channels" (to
                // the right of "Try again" in the same Row) would never be reachable.
                if (event.type != KeyEventType.KeyDown || errorMessage != null) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        onSwitchChannel(-1)
                        true
                    }
                    Key.DirectionDown -> {
                        onSwitchChannel(1)
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        resizeModeIndex = (resizeModeIndex + 1) % ResizeModes.size
                        hintLabel = ResizeModes[resizeModeIndex].second
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = ResizeModes[resizeModeIndex].first
                view.useController = errorMessage == null
                if (errorMessage != null) view.hideController()
            },
        )
        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        hintLabel?.let { label ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .background(BgSurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(text = label, color = White, style = MaterialTheme.typography.titleMedium)
            }
        }
        errorMessage?.let { message ->
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = "Couldn't play ${playback.name}: $message",
                        color = White,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                errorMessage = null
                                isBuffering = true
                                retryToken++
                            },
                            modifier = Modifier.focusRequester(tryAgainFocusRequester),
                            colors = ButtonDefaults.colors(containerColor = ThemeHighlight, contentColor = White),
                        ) {
                            Text("Try again")
                        }
                        OutlinedButton(onClick = onBack) {
                            Text("Back to channels")
                        }
                    }
                }
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
