package com.txao.authenticator

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val vault = Vault(File(app.filesDir, "accounts.bin"))

    val accounts = mutableStateListOf<Account>()

    /** An otpauth link that arrived from outside the app and is waiting for the user to confirm. */
    var incoming by mutableStateOf<String?>(null)

    /** Set when the vault could not be decrypted, so we never overwrite it with an empty list. */
    var loadFailed by mutableStateOf(false)
        private set

    init {
        try {
            accounts.addAll(vault.load())
        } catch (e: Exception) {
            loadFailed = true
        }
    }

    private fun persist() {
        if (!loadFailed) vault.save(accounts.toList())
    }

    fun add(account: Account) {
        accounts.add(account)
        persist()
    }

    fun update(account: Account) {
        val i = accounts.indexOfFirst { it.id == account.id }
        if (i >= 0) {
            accounts[i] = account
            persist()
        }
    }

    fun remove(account: Account) {
        accounts.removeAll { it.id == account.id }
        persist()
    }

    /** HOTP: advance to the next code. */
    fun nextCounter(account: Account) = update(account.copy(counter = account.counter + 1))
}
