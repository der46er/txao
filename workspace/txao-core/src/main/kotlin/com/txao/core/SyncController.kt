package com.txao.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SyncState {
    data object Off : SyncState
    data object Idle : SyncState
    data object Syncing : SyncState
    class Done(val at: Long) : SyncState
    class Failed(val message: String) : SyncState
}

/**
 * Runs sync in the background of the app process. Sync only ever runs if the user set it up.
 * [onSynced] runs after a successful sync (reschedule reminders, refresh lists, ...).
 */
class SyncController(
    private val prefs: TxaoPrefs,
    private val scope: CoroutineScope,
    private val store: SyncStore,
    private val onSynced: () -> Unit = {},
) {
    var state by mutableStateOf<SyncState>(if (prefs.syncConfigured) SyncState.Idle else SyncState.Off)
        private set
    private var job: Job? = null
    private val mutex = Mutex()

    /** Debounced: many edits in a row become one sync. */
    fun requestSync(delayMs: Long = 2500) {
        if (!prefs.syncConfigured) return
        job?.cancel()
        job = scope.launch { delay(delayMs); syncNow() }
    }

    suspend fun syncNow() {
        if (!prefs.syncConfigured) { state = SyncState.Off; return }
        mutex.withLock {
            state = SyncState.Syncing
            state = try {
                val engine = buildEngine()
                withContext(Dispatchers.IO) { engine.syncOnce() }
                onSynced()
                SyncState.Done(System.currentTimeMillis())
            } catch (e: Exception) {
                SyncState.Failed(friendly(e))
            }
        }
    }

    /** Call after the user switches sync on. */
    fun begin() { state = SyncState.Idle; scope.launch { syncNow() } }

    /** Checks the address, access code and key without changing anything. Returns an error or null. */
    suspend fun testConnection(url: String, code: String, key: String, allowHttp: Boolean): String? {
        checkUrl(url, allowHttp)?.let { return it }
        val bytes = TxaoCrypto.parseKey(key) ?: return "That isn't a valid sync key"
        return try {
            withContext(Dispatchers.IO) { HttpTransport(url, TxaoCrypto.derive(bytes, prefs.app).authToken, code).changes(Long.MAX_VALUE) }
            null
        } catch (e: Exception) { friendly(e) }
    }

    private fun buildEngine(): ItemSyncEngine {
        val keys = TxaoCrypto.derive(TxaoCrypto.parseKey(prefs.syncKey ?: "") ?: throw SyncException("No valid sync key"), prefs.app)
        checkUrl(prefs.serverUrl, prefs.allowHttp)?.let { throw SyncException(it) }
        return ItemSyncEngine(store, keys, HttpTransport(prefs.serverUrl, keys.authToken, prefs.accessCode, "Txao"))
    }

    private fun checkUrl(url: String, allowHttp: Boolean): String? {
        if (!HttpTransport.validUrl(url)) return "Enter the server address, like https://txao.example.com"
        if (url.trim().startsWith("http://") && !allowHttp) return "http:// is unencrypted. Use https://, or allow http for a local server below"
        return null
    }

    private fun friendly(e: Exception): String = when (e) {
        is SyncException -> e.message ?: "Sync failed"
        is java.net.UnknownHostException -> "Can't find that server. Check the address and your connection"
        is java.net.SocketTimeoutException, is java.net.ConnectException -> "Couldn't reach the server"
        is javax.net.ssl.SSLException -> "The server's HTTPS certificate isn't trusted"
        else -> "Sync failed (${e.javaClass.simpleName})"
    }
}
