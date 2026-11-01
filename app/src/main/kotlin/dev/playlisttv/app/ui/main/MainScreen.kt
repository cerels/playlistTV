package dev.playlisttv.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.playlisttv.app.Playback
import dev.playlisttv.app.R
import dev.playlisttv.app.data.Channel
import dev.playlisttv.app.ui.components.AddPlaylistForm
import dev.playlisttv.app.ui.components.CategoryItem
import dev.playlisttv.app.ui.components.ChannelRow
import dev.playlisttv.app.ui.theme.BgBase
import dev.playlisttv.app.ui.theme.Dim
import dev.playlisttv.app.ui.theme.ThemeDark
import dev.playlisttv.app.ui.theme.ThemeHighlight

private const val CAT_ALL = "All channels"
private const val CAT_FAVOURITES = "Favourites"
private const val CAT_RECENT = "Recently watched"

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChannelsScreen(
        state = uiState,
        onAddPlaylist = viewModel::addPlaylist,
        onShowAddForm = viewModel::setShowAddForm,
        onToggleFavourite = viewModel::toggleFavourite,
        onToggleHidden = viewModel::toggleHiddenCategory,
        onSetShowHidden = viewModel::setShowHidden,
        onWatch = { channel ->
            viewModel.markWatched(channel)
            onItemClick(Playback(channel.name, channel.url, channel.userAgent, channel.referrer, channel.headers))
        },
        modifier = modifier,
    )
}

@Composable
internal fun ChannelsScreen(
    state: ChannelsUiState,
    onAddPlaylist: (String, String) -> Unit,
    onShowAddForm: (Boolean) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
    onToggleHidden: (String) -> Unit,
    onSetShowHidden: (Boolean) -> Unit,
    onWatch: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(BgBase)) {
        ChannelsHeader(
            playlistName = state.saved.playlists.find { it.id == state.currentPlaylistId }?.name,
            hasPlaylists = state.saved.playlists.isNotEmpty(),
            showHidden = state.saved.settings.showHidden,
            onAddPlaylist = { onShowAddForm(true) },
            onToggleShowHidden = { onSetShowHidden(!state.saved.settings.showHidden) },
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.showAddForm -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                AddPlaylistForm(
                    busy = state.busy,
                    error = state.error,
                    onSubmit = onAddPlaylist,
                    onCancel = if (state.saved.playlists.isNotEmpty()) ({ onShowAddForm(false) }) else null,
                )
            }
            else -> ChannelsBody(state, onToggleFavourite, onToggleHidden, onWatch)
        }
    }
}

@Composable
private fun ChannelsBody(
    state: ChannelsUiState,
    onToggleFavourite: (Channel) -> Unit,
    onToggleHidden: (String) -> Unit,
    onWatch: (Channel) -> Unit,
) {
    var selectedCategory by rememberSaveable(state.currentPlaylistId) { mutableStateOf(CAT_ALL) }
    val pid = state.currentPlaylistId
    val showHidden = state.saved.settings.showHidden
    fun isHidden(group: String) = pid != null && state.saved.isHidden(pid, group)

    val visibleChannels = remember(state.channels, showHidden, state.saved, pid) {
        if (showHidden) state.channels else state.channels.filter { !isHidden(it.group) }
    }
    val groups = remember(state.channels, showHidden, state.saved, pid) {
        val all = state.channels.map { it.group }.distinct().sorted()
        if (showHidden) all else all.filter { !isHidden(it) }
    }
    val recentChannels = remember(state.channels, state.saved, pid) {
        val keys = pid?.let { state.saved.recent[it] } ?: emptyList()
        keys.mapNotNull { k -> state.channels.find { it.key == k } }
    }
    val favChannels = remember(visibleChannels, state.saved) { visibleChannels.filter { state.saved.isFav(it.key) } }

    val shown = when (selectedCategory) {
        CAT_ALL -> visibleChannels
        CAT_FAVOURITES -> favChannels
        CAT_RECENT -> recentChannels
        else -> visibleChannels.filter { it.group == selectedCategory }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.width(320.dp).fillMaxHeight(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                CategoryItem(
                    label = CAT_ALL,
                    count = visibleChannels.size,
                    selected = selectedCategory == CAT_ALL,
                    hidden = false,
                    onClick = { selectedCategory = CAT_ALL },
                )
            }
            item {
                CategoryItem(
                    label = CAT_FAVOURITES,
                    count = favChannels.size,
                    selected = selectedCategory == CAT_FAVOURITES,
                    hidden = false,
                    onClick = { selectedCategory = CAT_FAVOURITES },
                )
            }
            if (recentChannels.isNotEmpty()) {
                item {
                    CategoryItem(
                        label = CAT_RECENT,
                        count = recentChannels.size,
                        selected = selectedCategory == CAT_RECENT,
                        hidden = false,
                        onClick = { selectedCategory = CAT_RECENT },
                    )
                }
            }
            items(groups) { g ->
                CategoryItem(
                    label = g,
                    count = state.channels.count { it.group == g },
                    selected = selectedCategory == g,
                    hidden = isHidden(g),
                    onClick = { selectedCategory = g },
                    onLongClick = { onToggleHidden(g) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (shown.isEmpty()) {
                item { Text("No channels here.", color = Dim) }
            }
            items(shown, key = { it.key + it.pos }) { channel ->
                ChannelRow(
                    number = channel.pos,
                    name = channel.name,
                    group = channel.group,
                    isFavourite = state.saved.isFav(channel.key),
                    onClick = { onWatch(channel) },
                    onLongClick = { onToggleFavourite(channel) },
                )
            }
        }
    }
}

@Composable
private fun ChannelsHeader(
    playlistName: String?,
    hasPlaylists: Boolean,
    showHidden: Boolean,
    onAddPlaylist: () -> Unit,
    onToggleShowHidden: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ThemeDark).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            if (playlistName != null) {
                Text(text = playlistName, style = MaterialTheme.typography.bodyMedium, color = Dim)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasPlaylists) {
                OutlinedButton(onClick = onToggleShowHidden) {
                    Text(if (showHidden) "Hide hidden" else "Show hidden")
                }
            }
            Button(onClick = onAddPlaylist, colors = ButtonDefaults.colors(containerColor = ThemeHighlight)) {
                Text("+ Add playlist")
            }
        }
    }
}
