package com.txao.authenticator

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the account list (including the secrets) encrypted with an AES-256-GCM key that lives in the
 * Android Keystore and never leaves it. The file on disk is `iv || ciphertext`.
 */
class Vault(private val file: File) {
    private val keyAlias = "txao_authenticator_vault"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun load(): List<Account> {
        if (!file.exists()) return emptyList()
        val blob = file.readBytes()
        if (blob.size <= IV_BYTES) return emptyList()
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        val json = String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES), Charsets.UTF_8)
        return Account.listFromJson(json)
    }

    fun save(accounts: List<Account>) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key()) // Keystore generates a fresh random IV each time.
        val encrypted = cipher.doFinal(Account.listToJson(accounts).toByteArray(Charsets.UTF_8))
        // Write to a temp file first so a crash mid-write cannot destroy the existing vault.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(cipher.iv + encrypted)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "could not write vault" }
        }
    }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
