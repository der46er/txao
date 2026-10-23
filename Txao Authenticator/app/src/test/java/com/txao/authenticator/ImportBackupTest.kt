package com.txao.authenticator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ImportBackupTest {
    private val key20 = "12345678901234567890".toByteArray()

    // Reference values computed with an independent implementation of the Steam Guard algorithm.
    @Test fun steamCodes() {
        assertEquals("PV9M4", Otp.steam(key20, 59))
        assertEquals("PY4YB", Otp.steam(key20, 1111111109))
        assertEquals("VHHQY", Otp.steam(key20, 1234567890))
    }

    @Test fun steamUriForms() {
        val a = Account.fromUri("steam://GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")!!
        assertEquals(OtpType.STEAM, a.type)
        assertEquals("Steam", a.issuer)
        assertEquals("PV9M4", a.code(59_000))
        val b = Account.fromUri("otpauth://steam/Steam:me?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&digits=8")!!
        assertEquals(5, b.digits)
        assertEquals("PV9M4", b.code(59_000))
        assertEquals(b.copy(id = ""), Account.fromUri(b.toUri())!!.copy(id = ""))
    }

    @Test fun googleAuthenticatorMigrationLink() {
        val link = "otpauth-migration://offline?data=CjoKFDEyMzQ1Njc4OTAxMjM0NTY3ODkwEhZBQ01FOmFsaWNlQGV4YW1wbGUuY29tGgRBQ01FIAEoATACChUKBmZvb2JhchIDYm9iIAIoAjABOAcQARgBIAA%3D"
        val r = Importer.parse(link)
        assertEquals(0, r.skipped)
        assertEquals(2, r.accounts.size)
        val a = r.accounts[0]
        assertEquals("ACME", a.issuer)
        assertEquals("alice@example.com", a.label)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", a.secret)
        assertEquals(OtpType.TOTP, a.type)
        assertEquals("287082", a.code(59_000)) // RFC 6238 SHA-1 vector at t=59, 6 digits
        val b = r.accounts[1]
        assertEquals(OtpType.HOTP, b.type)
        assertEquals(7L, b.counter)
        assertEquals(8, b.digits)
        assertEquals(OtpAlgorithm.SHA256, b.algorithm)
        assertEquals("MZXW6YTBOI", b.secret)
    }

    @Test fun plainLinkListSkipsBadLines() {
        val r = Importer.parse(
            """
            otpauth://totp/A:one?secret=JBSWY3DPEHPK3PXP&issuer=A

            not a link
            otpauth://hotp/B:two?secret=JBSWY3DPEHPK3PXP&counter=3
            """.trimIndent(),
        )
        assertEquals(2, r.accounts.size)
        assertEquals(1, r.skipped)
    }

    @Test fun aegisPlainJson() {
        val json = """{"version":1,"header":{"slots":null,"params":null},"db":{"version":2,"entries":[
            {"type":"totp","name":"me@x.com","issuer":"X","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA256","digits":8,"period":60}},
            {"type":"steam","name":"s","issuer":"Steam","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":5,"period":30}},
            {"type":"yandex","name":"?","issuer":"?","info":{"secret":"JBSWY3DPEHPK3PXP"}}]}}"""
        val r = Importer.parse(json)
        assertEquals(2, r.accounts.size)
        assertEquals(1, r.skipped)
        assertEquals(OtpAlgorithm.SHA256, r.accounts[0].algorithm)
        assertEquals(60, r.accounts[0].period)
        assertEquals(OtpType.STEAM, r.accounts[1].type)
    }

    @Test fun aegisEncryptedIsRejectedNotCrashing() {
        val r = Importer.parse("""{"version":1,"header":{},"db":"base64stuff"}""")
        assertTrue(r.accounts.isEmpty())
    }

    @Test fun backupRoundTrip() {
        val accounts = listOf(
            Account(issuer = "A", label = "a", secret = "JBSWY3DPEHPK3PXP"),
            Account(issuer = "B", label = "b", secret = "MZXW6YTBOI", type = OtpType.HOTP, counter = 4),
        )
        val file = Backup.encrypt(accounts, "correct horse".toCharArray(), iterations = 1000)
        assertTrue(Backup.isBackup(file))
        assertFalse(file.contains("JBSWY3DPEHPK3PXP"))
        assertEquals(accounts, Backup.decrypt(file, "correct horse".toCharArray()))
    }

    @Test fun backupRejectsWrongPasswordAndTampering() {
        val file = Backup.encrypt(listOf(Account(issuer = "A", label = "a", secret = "JBSWY3DP")), "pw-12345".toCharArray(), iterations = 1000)
        try { Backup.decrypt(file, "pw-54321".toCharArray()); fail() } catch (e: Backup.WrongPasswordOrCorrupt) { assertNotNull(e) }
        val tampered = file.replace("\"iterations\": 1000", "\"iterations\": 1001")
        try { Backup.decrypt(tampered, "pw-12345".toCharArray()); fail() } catch (e: Backup.WrongPasswordOrCorrupt) { assertNotNull(e) }
    }
}
