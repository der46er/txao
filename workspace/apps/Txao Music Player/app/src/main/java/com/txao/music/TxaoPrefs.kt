package com.txao.music

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** Appearance preferences shared with the other TXAO apps' settings layout. Private storage, excluded from backups. */
class TxaoPrefs(context: Context) : TxaoLook {
    private val p = context.getSharedPreferences("txao_settings", Context.MODE_PRIVATE)

    override var theme by IntPref(p, "theme", 0)
    override var dynamicColor by BoolPref(p, "dynamic", true)
    override var glass by BoolPref(p, "glass", true)
    override var haptics by BoolPref(p, "haptics", true)
}
