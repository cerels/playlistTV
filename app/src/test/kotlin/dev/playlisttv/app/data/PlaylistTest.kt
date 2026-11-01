package dev.playlisttv.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTest {
    @Test
    fun basic() {
        val p = parsePlaylist(
            "#EXTM3U url-tvg=\"http://g/x.xml\"\n" +
                "#EXTINF:-1 tvg-id=\"a.de\" tvg-logo=\"l\" group-title=\"News\",Alpha\n" +
                "#EXTVLCOPT:http-user-agent=UA\n" +
                "http://s/a.m3u8\n" +
                "#EXTINF:-1 catchup-type=\"x\",UK | Beta\n" +
                "#EXTGRP:Other\n" +
                "http://s/b|Referer=x\n",
        )
        assertEquals("http://g/x.xml", p.epgUrl)
        assertEquals(2, p.channels.size)
        assertEquals("News", p.channels[0].group)
        assertEquals("UA", p.channels[0].userAgent)
        assertEquals("UK", p.channels[1].group)
        assertEquals("Beta", p.channels[1].name)
        assertEquals(Kind.LIVE, p.channels[1].kind)
        assertEquals("Beta", p.channels[1].tvgId)
    }
}
