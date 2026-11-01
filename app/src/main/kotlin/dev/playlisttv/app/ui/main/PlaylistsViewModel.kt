package dev.playlisttv.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.playlisttv.app.data.AppStore
import dev.playlisttv.app.data.Channel
import dev.playlisttv.app.data.PlaylistCfg
import dev.playlisttv.app.data.SavedState
import dev.playlisttv.app.data.parsePlaylist
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChannelsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val saved: SavedState = SavedState(),
    val channels: List<Channel> = emptyList(),
    val currentPlaylistId: Long? = null,
    val showAddForm: Boolean = false,
    val error: String? = null,
)

/**
 * Holds [SavedState] plus the currently loaded channel list, and performs the
 * downloads/parses/saves the UI triggers. There is deliberately no separate
 * repository layer: the logic here is small enough that one more
 * indirection would only make it harder to follow.
 */
class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {
    private val dir: File get() = getApplication<Application>().filesDir

    private val _uiState = MutableStateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { AppStore.load(dir) }
            _uiState.update { it.copy(saved = saved, loading = false, showAddForm = saved.playlists.isEmpty()) }
            loadCached(saved, saved.lastPlaylist ?: saved.playlists.firstOrNull()?.id)
        }
    }

    private suspend fun loadCached(saved: SavedState, playlistId: Long?) {
        val pid = playlistId ?: return
        if (saved.playlists.none { it.id == pid }) return
        val file = AppStore.playlistCache(dir, pid)
        if (!file.exists()) return
        val parsed = withContext(Dispatchers.IO) { parsePlaylist(file.readText()) }
        _uiState.update { it.copy(channels = parsed.channels, currentPlaylistId = pid) }
    }

    fun selectPlaylist(id: Long) {
        viewModelScope.launch {
            val saved = _uiState.value.saved.copy(lastPlaylist = id)
            _uiState.update { it.copy(saved = saved) }
            withContext(Dispatchers.IO) { AppStore.save(saved, dir) }
            loadCached(saved, id)
        }
    }

    fun addPlaylist(name: String, source: String) {
        if (source.isBlank()) {
            _uiState.update { it.copy(error = "Enter a playlist URL or file path.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                val bytes = withContext(Dispatchers.IO) { AppStore.fetch(source) }
                val text = bytes.toString(Charsets.UTF_8)
                val parsed = parsePlaylist(text)
                val (id, saved1) = _uiState.value.saved.allocatePlaylistId()
                val cfg = PlaylistCfg(
                    id = id,
                    name = name.ifBlank { "Playlist $id" },
                    source = source,
                    headerEpg = parsed.epgUrl ?: "",
                )
                val saved2 = saved1.copy(playlists = saved1.playlists + cfg, lastPlaylist = id, firstRunDone = true)
                withContext(Dispatchers.IO) {
                    val cache = AppStore.playlistCache(dir, id)
                    cache.parentFile?.mkdirs()
                    cache.writeBytes(bytes)
                    AppStore.save(saved2, dir)
                }
                _uiState.update {
                    it.copy(
                        saved = saved2,
                        channels = parsed.channels,
                        currentPlaylistId = id,
                        busy = false,
                        showAddForm = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.message ?: "Could not load the playlist") }
            }
        }
    }

    fun setShowAddForm(show: Boolean) {
        _uiState.update { it.copy(showAddForm = show, error = null) }
    }

    fun toggleFavourite(channel: Channel) = updateSaved { it.toggleFav(channel.key) }

    fun toggleHiddenCategory(group: String) {
        val pid = _uiState.value.currentPlaylistId ?: return
        updateSaved { it.setHidden(pid, group, !it.isHidden(pid, group)) }
    }

    fun setShowHidden(show: Boolean) = updateSaved { it.copy(settings = it.settings.copy(showHidden = show)) }

    fun markWatched(channel: Channel) {
        val pid = _uiState.value.currentPlaylistId ?: return
        updateSaved { it.pushRecent(pid, channel.key) }
    }

    private fun updateSaved(transform: (SavedState) -> SavedState) {
        val updated = transform(_uiState.value.saved)
        _uiState.update { it.copy(saved = updated) }
        viewModelScope.launch(Dispatchers.IO) { AppStore.save(updated, dir) }
    }
}
