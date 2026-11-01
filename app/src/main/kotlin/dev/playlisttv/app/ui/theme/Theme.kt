package dev.playlisttv.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// The original app only ever runs dark (player/css/player.css:3-17), so there is
// no light theme to switch to here.
private val PlaylistTVColorScheme = darkColorScheme(
    primary = ThemeHighlight,
    onPrimary = White,
    primaryContainer = ThemeDark,
    onPrimaryContainer = White,
    secondary = ThemeLight,
    onSecondary = BgBase,
    background = BgBase,
    onBackground = White,
    surface = BgSurface,
    onSurface = White,
    surfaceVariant = BgSurfaceElevated,
    onSurfaceVariant = Dim,
)

@Composable
fun PlaylistTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PlaylistTVColorScheme,
        typography = Typography,
        content = content,
    )
}
