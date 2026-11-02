package dev.playlisttv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.playlisttv.app.data.PlaylistCfg
import dev.playlisttv.app.ui.theme.ThemeHighlight
import dev.playlisttv.app.ui.theme.White

/**
 * Overlay list for switching between loaded playlists. The original app only ever had one;
 * this is new UI on top of [dev.playlisttv.app.ui.main.PlaylistsViewModel.selectPlaylist],
 * which already supported multiple playlists with nothing to drive it. Mirrors
 * [AddPlaylistForm]'s dark card styling so the two overlays read as one family.
 */
@Composable
fun PlaylistSwitcher(
    playlists: List<PlaylistCfg>,
    currentId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xFF151E29), RoundedCornerShape(12.dp))
            .padding(24.dp)
            .width(420.dp)
            .heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Playlists", style = MaterialTheme.typography.titleLarge, color = White)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(playlists, key = { it.id }) { cfg ->
                PlaylistRow(
                    name = cfg.name,
                    current = cfg.id == currentId,
                    onClick = { onSelect(cfg.id) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(name: String, current: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (current) ThemeHighlight else Color(0xFF1E1F22),
            contentColor = White,
            focusedContainerColor = ThemeHighlight,
            focusedContentColor = White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            )
            if (current) Text(text = "✓", color = White)
        }
    }
}
