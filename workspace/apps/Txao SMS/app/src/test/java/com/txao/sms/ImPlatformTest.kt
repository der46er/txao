package com.txao.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImPlatformTest {
    @Test fun whatsappNeedsDigits() {
        assertEquals("https://wa.me/436641234567", ImPlatform.WhatsApp.link("+43 664 1234567"))
        assertNull(ImPlatform.WhatsApp.link("anna"))
    }

    @Test fun handlesBecomeProfileLinks() {
        assertEquals("https://t.me/anna", ImPlatform.Telegram.link("@anna"))
        assertEquals("https://x.com/anna", ImPlatform.X.link("@anna"))
        assertNull(ImPlatform.Other.link("anything"))
        assertNull(ImPlatform.Instagram.link("  "))
    }

    @Test fun unknownPlatformKeepsItsLabel() {
        val e = ImEntry("Matrix", "@a:b.c")
        assertEquals(ImPlatform.Other, e.known)
        assertEquals("Matrix", e.displayPlatform)
        assertEquals("Signal", ImEntry("signal", "x").displayPlatform)
    }
}
