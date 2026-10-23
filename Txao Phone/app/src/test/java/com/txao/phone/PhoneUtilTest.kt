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
}
