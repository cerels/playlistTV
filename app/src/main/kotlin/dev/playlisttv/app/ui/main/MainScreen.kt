package dev.playlisttv.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.playlisttv.app.R
import dev.playlisttv.app.ui.components.ChannelRow
import dev.playlisttv.app.ui.theme.BgBase
import dev.playlisttv.app.ui.theme.PlaylistTVTheme
import dev.playlisttv.app.ui.theme.ThemeDark

/** Placeholder channel until the real M3U/EPG data layer is ported from the Rust prototype. */
private data class SampleChannel(val number: Int, val name: String, val group: String, val favourite: Boolean)

private val sampleChannels = listOf(
    SampleChannel(1, "News Now", "News", favourite = true),
    SampleChannel(2, "Movie Central", "Movies", favourite = false),
    SampleChannel(3, "Sports Live", "Sports", favourite = true),
    SampleChannel(4, "Kids Zone", "Animation;Kids", favourite = false),
    SampleChannel(5, "Music Box", "Music", favourite = false),
    SampleChannel(6, "Nature World", "Documentary", favourite = false),
)

@Composable
fun MainScreen(onItemClick: (NavKey) -> Unit, modifier: Modifier = Modifier) {
    ChannelsScreen(modifier = modifier)
}

@Composable
internal fun ChannelsScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(BgBase)) {
        ChannelsHeader()
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(sampleChannels) { channel ->
                ChannelRow(
                    number = channel.number,
                    name = channel.name,
                    group = channel.group,
                    isFavourite = channel.favourite,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun ChannelsHeader() {
    Column(modifier = Modifier.fillMaxWidth().background(ThemeDark).padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ChannelsScreenPreview() {
    PlaylistTVTheme { ChannelsScreen() }
}
