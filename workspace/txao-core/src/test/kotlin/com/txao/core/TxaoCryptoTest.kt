package com.txao.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TxaoCryptoTest {
    private val key = ByteArray(32) { it.toByte() }

    @Test fun keyRoundTrip() {
        val s = TxaoCrypto.formatKey(key)
        assertArrayEquals(key, TxaoCrypto.parseKey(s.lowercase().replace("-", " ")))
        assertNull(TxaoCrypto.parseKey("TXAO-AAAA"))
    }

    /** Pinned vectors: "notes" must never change, or existing Txao Notes accounts stop working. */
    @Test fun notesNamespaceIsStable() {
        val k = TxaoCrypto.derive(key, "notes")
        assertEquals("af3affd07a31203d", k.authToken.take(16))
        assertEquals("41c4a0b1eb63cee4", TxaoCrypto.accountId(k).take(16))
    }

    @Test fun appsAreIsolated() {
        assertNotEquals(TxaoCrypto.derive(key, "notes").authToken, TxaoCrypto.derive(key, "gallery").authToken)
    }

    @Test fun encryptBindsToId() {
        val k = TxaoCrypto.derive(key, "gallery")
        val c = TxaoCrypto.encrypt(k, "hello".toByteArray(), "item1")
        assertEquals("hello", String(TxaoCrypto.decrypt(k, c, "item1")))
        val failed = runCatching { TxaoCrypto.decrypt(k, c, "item2") }.isFailure
        assertEquals(true, failed)
    }
}
