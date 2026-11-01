package dev.playlisttv.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.playlisttv.app.ui.theme.BgSurface
import dev.playlisttv.app.ui.theme.ThemeHighlight
import dev.playlisttv.app.ui.theme.White
import kotlin.math.absoluteValue

/**
 * A single row in the channel list. Mirrors the original app's selection style
 * (player/css/player.css:716, :901): a solid highlight fill with rounded
 * corners and a slight scale-up on focus, rather than a stock Material ripple.
 */
@Composable
fun ChannelRow(
    number: Int,
    name: String,
    group: String,
    isFavourite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = BgSurface,
            contentColor = White,
            focusedContainerColor = ThemeHighlight,
            focusedContentColor = White,
            pressedContainerColor = ThemeHighlight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = number.toString(),
                modifier = Modifier.width(40.dp),
                color = White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
            )
            ChannelInitials(name = name, modifier = Modifier.size(44.dp))
            Box(modifier = Modifier.padding(start = 16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isFavourite) {
                        Text(text = " ★", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** Coloured-initials placeholder in place of a downloaded logo (MECHANICS.md: "Chosen"). */
@Composable
private fun ChannelInitials(name: String, modifier: Modifier = Modifier) {
    val initials = name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
    val color = colorForName(name)

    Surface(
        onClick = {},
        modifier = modifier,
        enabled = false,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = color, contentColor = White),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Text(text = initials, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private val InitialsPalette = listOf(
    Color(0xFFB33951), Color(0xFF8C5E58), Color(0xFF5B6B8C),
    Color(0xFF4C7A5B), Color(0xFF8C6B4C), Color(0xFF6B4C8C),
)

private fun colorForName(name: String): Color =
    InitialsPalette[name.hashCode().absoluteValue % InitialsPalette.size]
