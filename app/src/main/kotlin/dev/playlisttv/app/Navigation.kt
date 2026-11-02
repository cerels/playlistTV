package dev.playlisttv.app

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.playlisttv.app.ui.main.MainScreen
import dev.playlisttv.app.ui.main.PlaylistsViewModel
import dev.playlisttv.app.ui.player.PlayerScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(onItemClick = { navKey -> backStack.add(navKey) })
        }
        entry<Playback> { playback ->
          // Same ViewModelStoreOwner as MainScreen's (no decorator scopes it per entry), so this
          // is the already-loaded playlist, not a fresh load — needed to tune by channel number
          // without a visible list on screen.
          val viewModel: PlaylistsViewModel = viewModel()
          val uiState by viewModel.uiState.collectAsStateWithLifecycle()
          PlayerScreen(
            playback = playback,
            onBack = { backStack.removeLastOrNull() },
            onSwitchChannel = { delta ->
              val pid = uiState.currentPlaylistId
              val ordered = uiState.channels
                .filter { pid == null || uiState.saved.settings.showHidden || !uiState.saved.isHidden(pid, it.group) }
                .sortedBy { it.pos }
              val index = ordered.indexOfFirst { it.key == playback.channelKey }
              if (ordered.isNotEmpty() && index >= 0) {
                val next = ordered[(index + delta).mod(ordered.size)]
                viewModel.markWatched(next)
                backStack[backStack.lastIndex] =
                  Playback(next.name, next.url, next.userAgent, next.referrer, next.headers, next.key)
              }
            },
          )
        }
      },
  )
}
