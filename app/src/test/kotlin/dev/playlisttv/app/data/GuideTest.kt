package dev.playlisttv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTest {
    @Test
    fun parseSmall() {
        val xml = (
            "<?xml version=\"1.0\"?><tv>" +
                "<channel id=\"a.de\"><display-name>Alpha</display-name><icon src=\"http://i\"/></channel>" +
                "<channel id=\"z\"><display-name>Zed</display-name></channel>" +
                "<programme start=\"20240101100000 +0000\" stop=\"20240101110000 +0000\" channel=\"a.de\">" +
                "<title>News &amp; More</title><desc>Short</desc></programme>" +
                "<programme start=\"20240101110000 +0000\" stop=\"20240101110100 +0000\" channel=\"a.de\">" +
                "<title>Tiny</title></programme>" +
                "<programme start=\"20240101100000 +0000\" stop=\"20240101110000 +0000\" channel=\"z\">" +
                "<title>Skip</title></programme>" +
                "</tv>"
            ).toByteArray()
        val wanted = setOf("Alpha")
        val now = Clock.parseXmltv("20240101103000")!!
        val g = parseGuide(xml, wanted, GuideWindow(keepPastDays = 1, futureDays = 2, shiftHours = 0.0, now = now))
        val id = g.channelId("", "Alpha", "")!!
        val list = g.programmes.getValue(id)
        assertEquals(1, list.size)
        assertEquals("News & More", list[0].title)
        assertEquals("", list[0].desc)
        assertTrue(g.programmes["z"].isNullOrEmpty())
        assertEquals(0, onAir(list, now))
    }

    @Test
    fun looseNames() {
        assertEquals("canalcapital", loose("Canal Capital (1080p)"))
        assertEquals("canalcapitalco", loose("CanalCapital.co@SD"))
        assertEquals("canalcapitalco", loose("Canal.Capital.co"))
        assertEquals("caracolhd2", loose("Caracol HD2 [Geo-blocked]"))
    }
}
