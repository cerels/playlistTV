package dev.playlisttv.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.playlisttv.app.ui.theme.BgSurface
import dev.playlisttv.app.ui.theme.Dim
import dev.playlisttv.app.ui.theme.ThemeHighlight
import dev.playlisttv.app.ui.theme.White

/**
 * A row in the category sidebar (the original's `#nav` pane). Selected state
 * uses the same highlight fill as [ChannelRow]; `hidden` categories are drawn
 * dimmer so "show hidden" has a visible effect (MECHANICS.md "Category hiding").
 *
 * Long-press toggles hidden/shown — there's no secondary click on a remote,
 * so this takes the place of a context menu for that one action.
 */
@Composable
fun CategoryItem(
    label: String,
    count: Int,
    selected: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) ThemeHighlight else BgSurface,
            contentColor = if (hidden) Dim else White,
            focusedContainerColor = ThemeHighlight,
            focusedContentColor = White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(text = count.toString(), style = MaterialTheme.typography.bodyMedium, color = Dim)
        }
    }
}
