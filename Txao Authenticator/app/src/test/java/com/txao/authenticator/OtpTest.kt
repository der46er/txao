package com.txao.authenticator

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpTest {
    private val ascii = { s: String -> s.toByteArray(Charsets.US_ASCII) }

    // RFC 4226 appendix D
    @Test fun hotpRfc4226Vectors() {
        val key = ascii("12345678901234567890")
        val expected = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")
        expected.forEachIndexed { counter, code -> assertEquals(code, Otp.hotp(key, counter.toLong())) }
    }

    // RFC 6238 appendix B (8 digits; each hash uses a key of its own block-friendly length)
    @Test fun totpRfc6238Vectors() {
        val k1 = ascii("12345678901234567890")
        val k256 = ascii("12345678901234567890123456789012")
        val k512 = ascii("1234567890123456789012345678901234567890123456789012345678901234")
        val cases = listOf(
            59L to listOf("94287082", "46119246", "90693936"),
            1111111109L to listOf("07081804", "68084774", "25091201"),
            1111111111L to listOf("14050471", "67062674", "99943326"),
            1234567890L to listOf("89005924", "91819424", "93441116"),
            2000000000L to listOf("69279037", "90698825", "38618901"),
            20000000000L to listOf("65353130", "77737706", "47863826"),
        )
        for ((t, codes) in cases) {
            assertEquals(codes[0], Otp.totp(k1, t, 30, 8, OtpAlgorithm.SHA1))
            assertEquals(codes[1], Otp.totp(k256, t, 30, 8, OtpAlgorithm.SHA256))
            assertEquals(codes[2], Otp.totp(k512, t, 30, 8, OtpAlgorithm.SHA512))
        }
    }

    @Test fun secondsRemainingRollsOverAtPeriod() {
        assertEquals(30, Otp.secondsRemaining(60_000, 30))
        assertEquals(1, Otp.secondsRemaining(89_001, 30))
        assertEquals(15, Otp.secondsRemaining(75_000, 30))
    }

    @Test fun formatGroupsDigits() {
        assertEquals("123 456", Otp.format("123456"))
        assertEquals("123 4567", Otp.format("1234567"))
        assertEquals("1234 5678", Otp.format("12345678"))
    }

    // RFC 4648 section 10
    @Test fun base32Rfc4648Vectors() {
        val cases = mapOf("" to "", "f" to "MY======", "fo" to "MZXQ====", "foo" to "MZXW6===", "foob" to "MZXW6YQ=", "fooba" to "MZXW6YTB", "foobar" to "MZXW6YTBOI======")
        for ((plain, enc) in cases) {
            assertEquals(enc, Base32.encode(ascii(plain)))
            if (plain.isNotEmpty()) assertArrayEquals(ascii(plain), Base32.decode(enc))
        }
    }

    @Test fun base32DecodeIsLenient() {
        assertArrayEquals(ascii("foobar"), Base32.decode("mzxw 6ytb-oi"))
        assertNull(Base32.decode("MZXW1"))   // '1' is not in the alphabet
        assertNull(Base32.decode("M"))       // impossible length
        assertNull(Base32.decode(""))
    }

    @Test fun uriParsesIssuerLabelAndParams() {
        val a = Account.fromUri("otpauth://totp/ACME%20Co:john@example.com?secret=hxdm vb7y-jbsg&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60")!!
        assertEquals("ACME Co", a.issuer)
        assertEquals("john@example.com", a.label)
        assertEquals("HXDMVB7YJBSG", a.secret)
        assertEquals(OtpAlgorithm.SHA256, a.algorithm)
        assertEquals(8, a.digits)
        assertEquals(60, a.period)
        assertEquals(OtpType.TOTP, a.type)
    }

    @Test fun uriDefaultsAndHotpCounter() {
        val a = Account.fromUri("otpauth://hotp/user?secret=JBSWY3DPEHPK3PXP&counter=5")!!
        assertEquals("", a.issuer)
        assertEquals("user", a.label)
        assertEquals(OtpType.HOTP, a.type)
        assertEquals(5L, a.counter)
        assertEquals(6, a.digits)
        assertEquals(OtpAlgorithm.SHA1, a.algorithm)
    }

    @Test fun uriRejectsBadInput() {
        assertNull(Account.fromUri("https://example.com"))
        assertNull(Account.fromUri("otpauth://totp/x"))                               // no secret
        assertNull(Account.fromUri("otpauth://totp/x?secret=!!!"))                    // bad base32
        assertNull(Account.fromUri("otpauth://totp/x?secret=JBSWY3DP&digits=12"))     // digits out of range
        assertNull(Account.fromUri("otpauth://totp/x?secret=JBSWY3DP&algorithm=MD5")) // unsupported algorithm
        assertNull(Account.fromUri("otpauth://yandex/x?secret=JBSWY3DP"))             // unsupported type
    }

    @Test fun uriAndJsonRoundTrip() {
        val a = Account(issuer = "My: Service", label = "a b@c.d", secret = "JBSWY3DPEHPK3PXP", algorithm = OtpAlgorithm.SHA512, digits = 7, period = 45)
        val b = Account.fromUri(a.toUri())!!
        assertEquals(a.copy(id = b.id), b)
        val c = Account.listFromJson(Account.listToJson(listOf(a)))
        assertEquals(listOf(a), c)
    }

    @Test fun accountCodeMatchesKnownTotp() {
        // secret = base32("12345678901234567890"), t = 59 s -> RFC 6238 SHA1 vector, last 6 digits
        val a = Account(issuer = "t", label = "", secret = Base32.encode(ascii("12345678901234567890")).trimEnd('='), digits = 8)
        assertEquals("94287082", a.code(59_000))
    }
}
