package com.txao.core

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class BoolPref(private val p: SharedPreferences, private val key: String, def: Boolean) : ReadWriteProperty<Any?, Boolean> {
    private var state by mutableStateOf(p.getBoolean(key, def))
    override fun getValue(thisRef: Any?, property: KProperty<*>) = state
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { state = value; p.edit().putBoolean(key, value).apply() }
}

class IntPref(private val p: SharedPreferences, private val key: String, def: Int) : ReadWriteProperty<Any?, Int> {
    private var state by mutableIntStateOf(p.getInt(key, def))
    override fun getValue(thisRef: Any?, property: KProperty<*>) = state
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { state = value; p.edit().putInt(key, value).apply() }
}

class StringPref(private val p: SharedPreferences, private val key: String, def: String) : ReadWriteProperty<Any?, String> {
    private var state by mutableStateOf(p.getString(key, def) ?: def)
    override fun getValue(thisRef: Any?, property: KProperty<*>) = state
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { state = value; p.edit().putString(key, value).apply() }
}

/** Wraps a secret with a hardware-backed Android Keystore AES-256-GCM key so it is not stored in the clear. */
class SecretStore(private val alias: String) {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(alias)) {
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            gen.generateKey()
        }
        return ks.getKey(alias, null) as SecretKey
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    fun decrypt(b64: String): String? = try {
        val raw = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        String(c.doFinal(raw, 12, raw.size - 12))
    } catch (_: Exception) { null }
}

/**
 * Appearance + sync preferences shared by every TXAO app. Private storage, excluded from backups.
 * [app] is the lowercase app id ("gallery", "calendar", ...); it names the Keystore alias.
 */
class TxaoPrefs(context: Context, val app: String) : TxaoLook {
    private val p = context.getSharedPreferences("txao_settings", Context.MODE_PRIVATE)
    private val secrets = SecretStore("txao_${app}_sync_key")

    override var theme by IntPref(p, "theme", 0)
    override var dynamicColor by BoolPref(p, "dynamic", true)
    override var glass by BoolPref(p, "glass", true)
    override var haptics by BoolPref(p, "haptics", true)

    var syncEnabled by BoolPref(p, "sync_on", false)
    var serverUrl by StringPref(p, "sync_url", "")
    var accessCode by StringPref(p, "sync_code", "")
    var allowHttp by BoolPref(p, "allow_http", false)

    private var keyState by mutableStateOf(p.getString("sync_key_enc", null)?.let { secrets.decrypt(it) })

    /** The user's sync key ("TXAO-XXXX-..."), or null if none is set. */
    var syncKey: String?
        get() = keyState
        set(v) {
            keyState = v
            p.edit().apply { if (v == null) remove("sync_key_enc") else putString("sync_key_enc", secrets.encrypt(v)) }.apply()
        }

    val syncConfigured: Boolean
        get() = syncEnabled && serverUrl.isNotBlank() && syncKey != null
}
