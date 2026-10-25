package com.txao.phone

import org.junit.Assert.*
import org.junit.Test

class PhoneUtilTest {
    @Test fun normalizeKeepsDialableChars() {
        assertEquals("+431234567", PhoneUtil.normalize(" +43 (1) 234-567 "))
        assertEquals("123", PhoneUtil.normalize("1+2+3"))
        assertEquals("*21#", PhoneUtil.normalize("*21#"))
    }

    @Test fun t9MapsLetters() {
        assertEquals("5646", PhoneUtil.t9("John"))
        assertEquals("2662", PhoneUtil.t9("Anna"))
    }

    @Test fun matchesByNumberOrKeypadName() {
        assertTrue(PhoneUtil.matches("564", "John Smith", "0"))
        assertTrue(PhoneUtil.matches("764", "John Smith", "0")) // second word "Smith" starts with 7
        assertTrue(PhoneUtil.matches("234", "Zed", "01234"))
        assertFalse(PhoneUtil.matches("999", "John", "0123"))
        assertTrue(PhoneUtil.matches("", "x", "y"))
    }

    @Test fun durationFormats() {
        assertEquals("0:05", PhoneUtil.duration(5))
        assertEquals("1:05:09", PhoneUtil.duration(3909))
    }

    @Test fun prettyLeavesInternationalAlone() {
        assertEquals("+431234567", PhoneUtil.pretty("+43 1234567"))
        assertEquals("0664 123 4567", PhoneUtil.pretty("06641234567"))
    }

    @Test fun sameNumberIgnoresPrefixAndSpacing() {
        assertTrue(PhoneUtil.sameNumber("+43 664 1234567", "06641234567"))
        assertFalse(PhoneUtil.sameNumber("0664 1234567", "0664 1234568"))
        assertFalse(PhoneUtil.sameNumber("", ""))
    }

    @Test fun sectionsGroupByInitial() {
        assertEquals('A', PhoneUtil.section("  anna"))
        assertEquals('#', PhoneUtil.section("1-800"))
        assertEquals('#', PhoneUtil.section(""))
    }

    @Test fun groupsConsecutiveCallsFromSameNumber() {
        fun e(id: Long, n: String) = CallEntry(id, n, null, 1, id, 0)
        val g = Data.group(listOf(e(5, "0664 1"), e(4, "06641"), e(3, "0699 2"), e(2, "0664 1")))
        assertEquals(listOf(2, 1, 1), g.map { it.count })
    }

    @Test fun frequentRanksOutgoingNumbers() {
        fun out(id: Long, n: String) = CallEntry(id, n, null, 2, id, 0)
        val f = Data.frequent(listOf(out(1, "111111"), out(2, "222222"), out(3, "222222")), emptySet())
        assertEquals("222222", f.first().number)
    }

    @Test fun countryFromInternationalPrefix() {
        assertEquals("Austria", PhoneUtil.countryOf("+43 664 2423015"))
        assertEquals("United States / Canada", PhoneUtil.countryOf("+1 555 0100"))
        assertEquals("Portugal", PhoneUtil.countryOf("+351 21 000 0000"))
        assertNull(PhoneUtil.countryOf("0664 2423015"))
    }

    @Test fun birthdayLabels() {
        assertEquals("14 Mar 1990", PhoneUtil.birthdayLabel("1990-03-14"))
        assertEquals("5 Dec", PhoneUtil.birthdayLabel("--12-05"))
        assertEquals("sometime", PhoneUtil.birthdayLabel("sometime"))
    }
}

class CallEntryKindTest {
    private fun e(type: Int) = CallEntry(1, "1", null, type, 0, 0)

    @org.junit.Test fun declinedCallsAreNotMissed() {
        org.junit.Assert.assertEquals("Declined", e(android.provider.CallLog.Calls.REJECTED_TYPE).kind)
        org.junit.Assert.assertFalse(e(android.provider.CallLog.Calls.REJECTED_TYPE).missed)
        org.junit.Assert.assertEquals("Missed", e(android.provider.CallLog.Calls.MISSED_TYPE).kind)
    }
}

class ImPlatformTest {
    @org.junit.Test fun linksOpenTheRightChat() {
        org.junit.Assert.assertEquals("https://wa.me/436642423015", ImPlatform.WhatsApp.link("+43 664 2423015"))
        org.junit.Assert.assertEquals("https://t.me/martin", ImPlatform.Telegram.link("@martin"))
        org.junit.Assert.assertEquals("tg://resolve?phone=436642423015", ImPlatform.Telegram.link("+43 664 2423015"))
        org.junit.Assert.assertEquals("https://signal.me/#u/martin.42", ImPlatform.Signal.link("martin.42"))
        org.junit.Assert.assertEquals("https://instagram.com/_u/martin", ImPlatform.Instagram.link("@martin"))
        org.junit.Assert.assertNull(ImPlatform.Discord.link("martin#1234"))
        org.junit.Assert.assertNull(ImPlatform.Other.link("anything"))
        org.junit.Assert.assertNull(ImPlatform.X.link("  "))
    }

    @org.junit.Test fun namesMapBackToPlatforms() {
        org.junit.Assert.assertEquals(ImPlatform.Signal, ImPlatform.fromName("signal"))
        org.junit.Assert.assertEquals(ImPlatform.Other, ImPlatform.fromName("Matrix"))
        org.junit.Assert.assertEquals("Matrix", ImEntry("Matrix", "@a:b").displayPlatform)
    }
}

class DialCodeTest {
    @org.junit.Test fun classifiesCodes() {
        org.junit.Assert.assertEquals(DialCode.Imei, DialCode.parse("*#06#"))
        org.junit.Assert.assertEquals(DialCode.Secret("4636"), DialCode.parse("*#*#4636#*#*"))
        org.junit.Assert.assertEquals(DialCode.Ussd("*123#"), DialCode.parse("*123#"))
        org.junit.Assert.assertEquals(DialCode.Ussd("#31#"), DialCode.parse("#31#"))
        org.junit.Assert.assertEquals(DialCode.Ussd("**21*+436641234567#"), DialCode.parse("**21*+436641234567#"))
        org.junit.Assert.assertEquals(DialCode.None, DialCode.parse("0664 1234567"))
        org.junit.Assert.assertEquals(DialCode.None, DialCode.parse("#"))
        org.junit.Assert.assertEquals(DialCode.None, DialCode.parse("123#"))
    }

    @org.junit.Test fun tiktokLink() {
        org.junit.Assert.assertEquals("https://www.tiktok.com/@martin", ImPlatform.TikTok.link("@martin"))
    }
}
