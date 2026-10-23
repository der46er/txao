package com.txao.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsLogicTest {
    @Test fun cleanKeepsPlusAndDigits() {
        assertEquals("+436641234567", Numbers.clean("+43 (664) 123-4567"))
        assertEquals("0664123", Numbers.clean("0664 123"))
        assertEquals("BANK", Numbers.clean(" BANK "))
    }

    @Test fun sameIgnoresCountryPrefix() {
        assertTrue(Numbers.same("+436641234567", "0664 1234567"))
        assertFalse(Numbers.same("+436641234567", "+436641234568"))
        assertTrue(Numbers.same("Bank", "BANK"))
        assertFalse(Numbers.same("Bank", "+436641234567"))
    }

    @Test fun parseRecipientsSplitsAndDedupes() {
        assertEquals(listOf("+4366412", "0664999"), Numbers.parseRecipients("+43 664 12, 0664999; +43 664 12"))
        assertTrue(Numbers.parseRecipients(" , ;").isEmpty())
    }

    @Test fun gsmSegments() {
        assertEquals(SmsText.Info(SmsText.Encoding.GSM, 0, 0, 160), SmsText.info(""))
        assertEquals(1, SmsText.info("a".repeat(160)).parts)
        val two = SmsText.info("a".repeat(161))
        assertEquals(2, two.parts)
        assertEquals(306 - 161, two.remaining)
    }

    @Test fun extendedGsmCharsCountDouble() {
        assertEquals(2, SmsText.info("€").length)
        assertEquals(SmsText.Encoding.GSM, SmsText.info("5€ [ok]").encoding)
    }

    @Test fun unicodeFallsBackTo70() {
        val one = SmsText.info("héllo 😀")
        assertEquals(SmsText.Encoding.UNICODE, one.encoding)
        assertEquals(1, one.parts)
        assertEquals(2, SmsText.info("ж".repeat(71)).parts)
    }

    @Test fun scheduledRoundTrips() {
        val list = listOf(Scheduled(1, "+43664", "hi \"there\"\nnew line", 1000L), Scheduled(2, "0664", "x", 2000L))
        assertEquals(list, Scheduled.listFromJson(Scheduled.listToJson(list)))
        assertTrue(Scheduled.listFromJson("not json").isEmpty())
        assertTrue(Scheduled.listFromJson(null).isEmpty())
    }
}
