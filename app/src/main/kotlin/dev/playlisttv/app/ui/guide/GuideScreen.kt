package dev.playlisttv.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.playlisttv.app.data.Channel
import dev.playlisttv.app.data.Clock
import dev.playlisttv.app.data.Guide
import dev.playlisttv.app.data.Programme
import dev.playlisttv.app.ui.theme.BgBase
import dev.playlisttv.app.ui.theme.BgSurface
import dev.playlisttv.app.ui.theme.Dim
import dev.playlisttv.app.ui.theme.ThemeDark
import dev.playlisttv.app.ui.theme.ThemeHighlight
import dev.playlisttv.app.ui.theme.ThemeLight
import dev.playlisttv.app.ui.theme.White

/** Row height and left name-column width, chosen for 10-foot legibility (TODO.md: "pick a TV-appropriate scale"). */
private val RowHeight = 72.dp
private val NameColumnWidth = 220.dp

/** TV-scaled timeline: wider than the original desktop 12px/min (MECHANICS.md "Guide grid") since blocks need to stay readable from a couch. */
private val MinuteWidth = 5.dp
private val MinBlockWidthForTitle = 90.dp

/**
 * The TV guide grid (TODO.md "EPG / TV guide grid screen"). Channels down the left, their
 * programmes laid out on a shared timeline to the right, synced scrolling in both axes, and a
 * "now" marker — the D-pad-navigable equivalent of the original's epg-overview.js grid.
 */
@Composable
fun GuideScreen(
    channels: List<Channel>,
    guide: Guide?,
    loading: Boolean,
    error: String?,
    isFavourite: (Channel) -> Boolean,
    onWatch: (Channel) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(BgBase)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(ThemeDark).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "TV Guide", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRefresh, enabled = !loading) {
                    Text(if (loading) "Refreshing…" else "Refresh guide")
                }
                Button(onClick = onBack, colors = ButtonDefaults.colors(containerColor = ThemeHighlight)) {
                    Text("Back to channels")
                }
            }
        }
        when {
            loading && guide == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = error, color = Color(0xFFFF8A80))
            }
            guide == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No guide loaded yet.", color = Dim)
            }
            else -> GuideGrid(
                channels = channels,
                guide = guide,
                isFavourite = isFavourite,
                onWatch = onWatch,
                onToggleFavourite = onToggleFavourite,
            )
        }
    }
}

private data class GuideRow(val channel: Channel, val programmes: List<Programme>)

@Composable
private fun GuideGrid(
    channels: List<Channel>,
    guide: Guide,
    isFavourite: (Channel) -> Boolean,
    onWatch: (Channel) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
) {
    val now = remember(guide) { Clock.now() }
    val rows = remember(channels, guide) {
        channels.map { c ->
            val xmltvId = guide.channelId(c.tvgId, c.name, c.tvgName)
            GuideRow(c, xmltvId?.let { guide.programmes[it] } ?: emptyList())
        }
    }
    val allProgrammes = remember(rows) { rows.flatMap { it.programmes } }

    if (allProgrammes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No guide data matches these channels.", color = Dim)
        }
        return
    }

    val timelineStart = remember(allProgrammes) { Clock.hourFloor(allProgrammes.minOf { it.start }) }
    val timelineEnd = remember(allProgrammes) {
        val maxStop = allProgrammes.maxOf { it.stop }
        val floored = Clock.hourFloor(maxStop)
        if (floored == maxStop) floored else floored + 3600
    }
    fun xFor(t: Long): Dp = MinuteWidth * ((t - timelineStart) / 60f)
    val totalWidth = xFor(timelineEnd)

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(NameColumnWidth))
            Box(Modifier.weight(1f).height(32.dp).horizontalScroll(hScroll)) {
                Box(Modifier.width(totalWidth).height(32.dp)) {
                    var t = timelineStart
                    while (t < timelineEnd) {
                        Text(
                            text = Clock.hm(t),
                            color = Dim,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.offset(x = xFor(t)).padding(start = 4.dp),
                        )
                        t += 3600
                    }
                }
            }
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(NameColumnWidth).fillMaxHeight().verticalScroll(vScroll)) {
                rows.forEach { r -> ChannelLabel(r.channel, isFavourite(r.channel)) }
            }
            Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                Column(Modifier.width(totalWidth).verticalScroll(vScroll)) {
                    rows.forEach { r ->
                        Box(Modifier.height(RowHeight).fillMaxWidth()) {
                            if (r.programmes.isEmpty()) {
                                Text(
                                    text = "No guide data",
                                    color = Dim,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterStart),
                                )
                            }
                            r.programmes.forEach { p ->
                                ProgrammeBlock(
                                    programme = p,
                                    x = xFor(p.start),
                                    width = xFor(p.stop) - xFor(p.start),
                                    live = now >= p.start && now < p.stop,
                                    onClick = { onWatch(r.channel) },
                                    onLongClick = { onToggleFavourite(r.channel) },
                                )
                            }
                        }
                    }
                }
                if (now in timelineStart..timelineEnd) {
                    Box(
                        Modifier
                            .offset(x = xFor(now))
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(ThemeLight),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelLabel(channel: Channel, favourite: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = channel.name + if (favourite) " ★" else "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = White,
        )
    }
}

@Composable
private fun ProgrammeBlock(
    programme: Programme,
    x: Dp,
    width: Dp,
    live: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val blockWidth = width.coerceAtLeast(24.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.offset(x = x).width(blockWidth).fillMaxHeight().padding(horizontal = 2.dp, vertical = 4.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (live) ThemeDark else BgSurface,
            contentColor = if (live) ThemeLight else White,
            focusedContainerColor = ThemeHighlight,
            focusedContentColor = White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        if (blockWidth >= MinBlockWidthForTitle) {
            Column(Modifier.padding(6.dp)) {
                Text(
                    text = (if (live) "● " else "") + programme.title.ifBlank { "(no title)" },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${Clock.hm(programme.start)}–${Clock.hm(programme.stop)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dim,
                )
            }
        }
    }
}
