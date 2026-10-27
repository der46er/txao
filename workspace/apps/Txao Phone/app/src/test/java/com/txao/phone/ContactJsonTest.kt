package com.txao.phone

import org.junit.Assert.*
import org.junit.Test

class ContactJsonTest {
    private val d = ContactDetails(
        name = "Ada Lovelace",
        phones = listOf(Labeled(2, null, "+43 660 1234567"), Labeled(1, "Home", "01 234")),
        emails = listOf(Labeled(1, null, "ada@example.com")),
        company = "Analytical Engines", notes = "Met at the fair", starred = true,
        ims = listOf(ImEntry("Telegram", "@ada")),
    )

    @Test fun roundTripKeepsContent() {
        val back = ContactJson.details(ContactJson.body(d, null, null))
        assertEquals(d.name, back.name)
        assertEquals(d.phones.map { it.value }, back.phones.map { it.value })
        assertEquals("Home", back.phones[1].customLabel)
        assertEquals(d.emails.map { it.value }, back.emails.map { it.value })
        assertEquals("Analytical Engines", back.company)
        assertTrue(back.starred)
        assertEquals("@ada", back.ims.single().handle)
    }

    @Test fun hashChangesWithContentAndPictures() {
        val a = ContactJson.hash(ContactJson.body(d, null, null))
        assertEquals(a, ContactJson.hash(ContactJson.body(d.copy(), null, null)))
        assertNotEquals(a, ContactJson.hash(ContactJson.body(d.copy(name = "Ada King"), null, null)))
        assertNotEquals(a, ContactJson.hash(ContactJson.body(d, "cp_x_1234", null)))
    }

    @Test fun blankEntriesAreDropped() {
        val j = ContactJson.body(d.copy(phones = d.phones + Labeled(2, null, "  ")), null, null)
        assertEquals(2, ContactJson.details(j).phones.size)
    }

    @Test fun emergencyBodyHasTypeNameAndNumber() {
        val e = ContactJson.emergencyBody("Mum", "+431")
        assertEquals("emergency", e.getString("t")); assertEquals("Mum", e.getString("name")); assertEquals("+431", e.getString("number"))
    }
}
