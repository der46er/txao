package com.txao.sms

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsCodecTest {
    private fun ByteArrayOutputStream.text(s: String) { write(s.toByteArray()); write(0) }

    @Test fun readsNotification() {
        val o = ByteArrayOutputStream()
        o.write(0x8C); o.write(0x82)
        o.write(0x98); o.text("TX42")
        o.write(0x8D); o.write(0x92)
        o.write(0x89); o.write(25); o.write(0x80); o.text("+436641234567/TYPE=PLMN")
        o.write(0x8A); o.write(0x80)
        o.write(0x8E); o.write(2); o.write(0x03); o.write(0xE8) // 1000 bytes
        o.write(0x83); o.text("http://mms.example/fetch?id=7")
        val n = MmsCodec.parseNotification(o.toByteArray())
        assertNotNull(n)
        assertEquals("TX42", n!!.transactionId)
        assertEquals("http://mms.example/fetch?id=7", n.location)
        assertEquals("+436641234567", n.from)
        assertEquals(1000L, n.size)
        assertEquals(MmsCodec.TYPE_NOTIFICATION_IND, MmsCodec.messageType(o.toByteArray()))
    }

    @Test fun garbageIsNull() {
        assertNull(MmsCodec.parseNotification(byteArrayOf(1, 2, 3)))
        assertNull(MmsCodec.parseRetrieve(ByteArray(0)))
    }

    @Test fun sendReqRoundTripsThroughTheReader() {
        val jpeg = ByteArray(300) { (it * 7).toByte() }
        val parts = listOf(
            MmsPart("text/plain", "text.txt", "text", "Hej, schau mal: ü €".toByteArray()),
            MmsPart("image/jpeg", "photo.jpg", "photo", jpeg),
        )
        val pdu = MmsCodec.buildSendReq(listOf("+436641234567"), "Urlaub", parts, "T1", 1_700_000_000L)
        assertEquals(MmsCodec.TYPE_SEND_REQ, MmsCodec.messageType(pdu))
        // A send-req has the same layout as a retrieve-conf apart from the message type, so patch it and read it back.
        val asRetrieve = pdu.copyOf().also { it[1] = 0x84.toByte() }
        val m = MmsCodec.parseRetrieve(asRetrieve)
        assertNotNull(m)
        assertEquals(listOf("+436641234567"), m!!.to)
        assertEquals("Urlaub", m.subject)
        assertEquals(1_700_000_000L, m.date)
        assertEquals(3, m.parts.size)
        assertTrue(m.parts[0].isSmil)
        assertEquals("Hej, schau mal: ü €", m.parts[1].text)
        assertEquals("text", m.parts[1].contentId)
        assertEquals("image/jpeg", m.parts[2].contentType)
        assertEquals("photo.jpg", m.parts[2].name)
        assertArrayEquals(jpeg, m.parts[2].data)
    }

    @Test fun smilHasOneSlidePerMedia() {
        val s = MmsCodec.smil(listOf(MmsPart("image/jpeg", "a.jpg", "a", ByteArray(1)), MmsPart("image/png", "b.png", "b", ByteArray(1))))
        assertEquals(2, s.split("<par ").size - 1)
        assertTrue(s.contains("src=\"b.png\""))
    }

    @Test fun readsRetrieveWithWellKnownTypesAndCharset() {
        // Hand-built like a carrier would send it: well-known image/jpeg, text/plain with ISO-8859-1.
        val o = ByteArrayOutputStream()
        o.write(0x8C); o.write(0x84)
        o.write(0x98); o.text("T9")
        o.write(0x8D); o.write(0x90)
        o.write(0x89); o.write(7); o.write(0x80); o.text("+4366")
        o.write(0x84); o.write(0xA3) // multipart.mixed, short form
        o.write(2)
        // part 1: text, charset 4 (latin-1), content-id
        val h1 = ByteArrayOutputStream().also { it.write(3); it.write(0x83); it.write(0x81); it.write(0x84); it.write(0xC0); it.write(0x22); it.text("<t>") }
        val d1 = byteArrayOf(0x6B, 0xFC.toByte(), 0x68) // "küh" in latin-1
        o.write(h1.size()); o.write(d1.size); o.write(h1.toByteArray()); o.write(d1)
        // part 2: image/jpeg as short integer
        val h2 = byteArrayOf(0x9E.toByte())
        val d2 = byteArrayOf(9, 8, 7)
        o.write(h2.size); o.write(d2.size); o.write(h2); o.write(d2)
        val m = MmsCodec.parseRetrieve(o.toByteArray())
        assertNotNull(m)
        assertEquals(2, m!!.parts.size)
        assertEquals("küh", m.parts[0].text)
        assertEquals("t", m.parts[0].contentId)
        assertEquals("image/jpeg", m.parts[1].contentType)
        assertArrayEquals(byteArrayOf(9, 8, 7), m.parts[1].data)
    }
}
