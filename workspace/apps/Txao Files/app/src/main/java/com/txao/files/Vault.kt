package com.txao.files

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chunked AES-256-GCM file encryption for the Locked folder.
 * Layout: magic(5) nonce(8) then repeated [ciphertext length (4, big endian)][ciphertext + tag];
 * chunk i uses the IV nonce || i, so big videos never have to fit in memory.
 */
object VaultCrypto {
    private val MAGIC = byteArrayOf('T'.code.toByte(), 'X'.code.toByte(), 'L'.code.toByte(), 'K'.code.toByte(), 1)
    private const val CHUNK = 64 * 1024
    private const val TAG_BITS = 128

    fun isEncrypted(f: File): Boolean = runCatching {
        f.inputStream().use { val b = ByteArray(MAGIC.size); readFully(it, b) == b.size && b.contentEquals(MAGIC) }
    }.getOrDefault(false)

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, index: Int): Cipher {
        val iv = nonce + byteArrayOf((index ushr 24).toByte(), (index ushr 16).toByte(), (index ushr 8).toByte(), index.toByte())
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv)) }
    }

    fun encrypt(key: ByteArray, input: InputStream, output: OutputStream) {
        val nonce = ByteArray(8).also { SecureRandom().nextBytes(it) }
        output.write(MAGIC); output.write(nonce)
        val buf = ByteArray(CHUNK)
        var index = 0
        while (true) {
            val n = readFully(input, buf)
            if (n <= 0) break
            val ct = cipher(Cipher.ENCRYPT_MODE, key, nonce, index++).doFinal(buf, 0, n)
            output.write(byteArrayOf((ct.size ushr 24).toByte(), (ct.size ushr 16).toByte(), (ct.size ushr 8).toByte(), ct.size.toByte()))
            output.write(ct)
            if (n < buf.size) break
        }
        output.flush()
    }

    /** Throws if the key is wrong or the data was tampered with. */
    fun decrypt(key: ByteArray, input: InputStream, output: OutputStream) {
        val head = ByteArray(MAGIC.size + 8)
        require(readFully(input, head) == head.size && head.copyOf(MAGIC.size).contentEquals(MAGIC)) { "Not a locked file" }
        val nonce = head.copyOfRange(MAGIC.size, head.size)
        val lenBuf = ByteArray(4)
        var index = 0
        while (readFully(input, lenBuf) == 4) {
            val len = ((lenBuf[0].toInt() and 0xFF) shl 24) or ((lenBuf[1].toInt() and 0xFF) shl 16) or
                ((lenBuf[2].toInt() and 0xFF) shl 8) or (lenBuf[3].toInt() and 0xFF)
            require(len in 16..(CHUNK + 16)) { "Corrupt locked file" }
            val ct = ByteArray(len)
            require(readFully(input, ct) == len) { "Truncated locked file" }
            output.write(cipher(Cipher.DECRYPT_MODE, key, nonce, index++).doFinal(ct))
        }
        output.flush()
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}

/**
 * The vault master key is random and never stored in the clear. It is wrapped either by an Android
 * Keystore key (phone lock mode) or by a key derived from the user's own password (password mode),
 * so switching modes only re-wraps 32 bytes instead of re-encrypting every file.
 */
object VaultKeys {
    private const val ALIAS = "txao_files_vault"
    private const val PBKDF2_ROUNDS = 210_000

    fun newMaster(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun seal(key: SecretKey, plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return b64(c.iv + c.doFinal(plain))
    }

    private fun open(key: SecretKey, blob: String): ByteArray? = runCatching {
        val raw = unb64(blob)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, raw, 0, 12)) }
        c.doFinal(raw, 12, raw.size - 12)
    }.getOrNull()

    private fun deviceKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256).build(),
                )
            }.generateKey()
        }
        return ks.getKey(ALIAS, null) as SecretKey
    }

    fun wrapDevice(master: ByteArray): String = seal(deviceKey(), master)

    fun unwrapDevice(blob: String): ByteArray? = runCatching { open(deviceKey(), blob) }.getOrNull()

    private fun passwordKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, PBKDF2_ROUNDS, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    /** Returns (salt, blob), both Base64, to be stored next to each other. */
    fun wrapPassword(master: ByteArray, password: CharArray): Pair<String, String> {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return b64(salt) to seal(passwordKey(password, salt), master)
    }

    /** Null when the password is wrong. */
    fun unwrapPassword(blob: String, salt: String, password: CharArray): ByteArray? =
        runCatching { open(passwordKey(password, unb64(salt)), blob) }.getOrNull()
}

/** File operations between the normal storage and the vault folder. */
object Vault {
    private fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        var n = 2
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        while (target.exists()) { target = File(dir, "$base ($n)$ext"); n++ }
        return target
    }

    /** Encrypts [src] (a file or a whole folder) into [destDir] and removes the original. */
    fun lock(master: ByteArray, src: File, destDir: File): Boolean {
        destDir.mkdirs()
        if (src.isDirectory) {
            val sub = uniqueTarget(destDir, src.name).also { it.mkdirs() }
            var ok = true
            src.listFiles()?.forEach { ok = lock(master, it, sub) && ok }
            if (ok) src.deleteRecursively()
            return ok
        }
        val target = uniqueTarget(destDir, src.name)
        return try {
            src.inputStream().use { i -> target.outputStream().use { o -> VaultCrypto.encrypt(master, i, o) } }
            src.delete()
        } catch (_: Exception) { target.delete(); false }
    }

    /** Decrypts [src] into [destDir] and removes it from the vault. */
    fun unlockOut(master: ByteArray, src: File, destDir: File): Boolean {
        destDir.mkdirs()
        if (src.isDirectory) {
            val sub = uniqueTarget(destDir, src.name).also { it.mkdirs() }
            var ok = true
            src.listFiles()?.forEach { ok = unlockOut(master, it, sub) && ok }
            if (ok) src.deleteRecursively()
            return ok
        }
        val target = uniqueTarget(destDir, src.name)
        return try {
            decryptTo(master, src, target)
            src.delete()
        } catch (_: Exception) { target.delete(); false }
    }

    /** Writes a readable copy of [src] to [target]; plain legacy files are copied as they are. */
    fun decryptTo(master: ByteArray, src: File, target: File) {
        target.parentFile?.mkdirs()
        src.inputStream().use { i ->
            target.outputStream().use { o ->
                if (VaultCrypto.isEncrypted(src)) VaultCrypto.decrypt(master, i, o) else i.copyTo(o)
            }
        }
    }

    /** Encrypts files that were locked by versions before 1.3.0, which stored them unencrypted. */
    fun migrateLegacy(master: ByteArray, dir: File) {
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) migrateLegacy(master, f)
            else if (!VaultCrypto.isEncrypted(f)) {
                val tmp = File(f.parentFile, f.name + ".tmp")
                try {
                    f.inputStream().use { i -> tmp.outputStream().use { o -> VaultCrypto.encrypt(master, i, o) } }
                    if (!f.delete() || !tmp.renameTo(f)) tmp.delete()
                } catch (_: Exception) { tmp.delete() }
            }
        }
    }
}
