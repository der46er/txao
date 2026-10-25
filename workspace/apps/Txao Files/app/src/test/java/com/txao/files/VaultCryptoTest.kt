package com.txao.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

class VaultCryptoTest {
    private val key = ByteArray(32) { it.toByte() }

    private fun roundTrip(data: ByteArray): ByteArray {
        val enc = ByteArrayOutputStream().also { VaultCrypto.encrypt(key, ByteArrayInputStream(data), it) }.toByteArray()
        return ByteArrayOutputStream().also { VaultCrypto.decrypt(key, ByteArrayInputStream(enc), it) }.toByteArray()
    }

    @Test fun emptySmallAndMultiChunkFilesSurvive() {
        for (size in listOf(0, 1, 100, 64 * 1024, 64 * 1024 + 1, 200_000)) {
            val data = ByteArray(size).also { Random(size.toLong()).nextBytes(it) }
            assertArrayEquals("size $size", data, roundTrip(data))
        }
    }

    @Test fun wrongKeyFails() {
        val enc = ByteArrayOutputStream().also { VaultCrypto.encrypt(key, ByteArrayInputStream(ByteArray(500)), it) }.toByteArray()
        assertThrows(Exception::class.java) {
            VaultCrypto.decrypt(ByteArray(32) { 9 }, ByteArrayInputStream(enc), ByteArrayOutputStream())
        }
    }

    @Test fun tamperedDataFails() {
        val enc = ByteArrayOutputStream().also { VaultCrypto.encrypt(key, ByteArrayInputStream(ByteArray(500)), it) }.toByteArray()
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            VaultCrypto.decrypt(key, ByteArrayInputStream(enc), ByteArrayOutputStream())
        }
    }
}
