package com.txao.authenticator

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val vault = Vault(File(app.filesDir, "accounts.bin"))
    val settings = Settings(app)

    val accounts = mutableStateListOf<Account>()

    var query by mutableStateOf("")

    /** The list as shown: filtered by the search box (service or account name). */
    val visible by derivedStateOf {
        val q = query.trim()
        if (q.isEmpty()) accounts.toList()
        else accounts.filter { it.issuer.contains(q, ignoreCase = true) || it.label.contains(q, ignoreCase = true) }
    }

    /** An otpauth link that arrived from outside the app and is waiting for the user to confirm. */
    var incoming by mutableStateOf<String?>(null)

    /** True until the user passes the device lock (only when the app lock setting is on). */
    var locked by mutableStateOf(settings.appLock)

    /**
     * Set just before opening a system picker or permission dialog: those cover our window, and the
     * app lock must not fire when the user comes straight back from them.
     */
    var skipNextLock = false

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

    /** Moves an account one place up (-1) or down (+1) in the full list. */
    fun move(account: Account, delta: Int) {
        val from = accounts.indexOfFirst { it.id == account.id }
        val to = from + delta
        if (from < 0 || to !in accounts.indices) return
        accounts.add(to, accounts.removeAt(from))
        persist()
    }

    private fun sameAccount(a: Account, b: Account) =
        a.secret == b.secret && a.issuer == b.issuer && a.label == b.label

    /** Adds the imported accounts that are not already present; returns how many were new. */
    fun addAll(imported: List<Account>): Int {
        val fresh = imported.filter { n -> accounts.none { sameAccount(it, n) } }
            .distinctBy { Triple(it.secret, it.issuer, it.label) }
        if (fresh.isNotEmpty()) {
            accounts.addAll(fresh)
            persist()
        }
        return fresh.size
    }

    fun encryptedBackup(password: CharArray): String = Backup.encrypt(accounts.toList(), password)

    /** One otpauth link per line. The caller must warn the user: this file holds every secret unencrypted. */
    fun plainExport(): String = accounts.joinToString("\n") { it.toUri() } + "\n"
}
