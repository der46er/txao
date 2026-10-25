package com.txao.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaletteTest {
    @Test fun parsesHexWithAndWithoutHash() {
        assertEquals(0xFFFF8800.toInt(), Hex.parse("#FF8800"))
        assertEquals(0xFFFF8800.toInt(), Hex.parse(" ff8800 "))
    }

    @Test fun rejectsBadHex() {
        assertNull(Hex.parse("#FF88"))
        assertNull(Hex.parse("GG8800"))
        assertNull(Hex.parse(""))
    }

    @Test fun formatsUpperCaseWithHash() {
        assertEquals("#00FF7F", Hex.format(0xFF00FF7F.toInt()))
    }

    @Test fun defaultPaletteHasTenValidColors() {
        assertEquals(10, DefaultPalette.size)
        assert(DefaultPalette.all { Hex.parse(it) != null })
    }

    @Test fun movedReordersAndIgnoresOutOfRange() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(0, 1))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").moved(0, -1))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").moved(2, 1))
    }
}
