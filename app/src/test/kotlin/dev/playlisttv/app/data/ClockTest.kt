package dev.playlisttv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClockTest {
    @Test
    fun xmltv() {
        assertEquals(0L, Clock.parseXmltv("19700101000000 +0000"))
        assertEquals(0L, Clock.parseXmltv("19700101010000 +0100"))
        assertEquals(0L, Clock.parseXmltv("19700101000000"))
        assertEquals(1709226000L, Clock.parseXmltv("20240229120000 -0500"))
        assertNull(Clock.parseXmltv("garbage"))
    }
}
