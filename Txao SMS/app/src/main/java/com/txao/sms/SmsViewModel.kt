package com.txao.sms

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val repo = SmsRepository(app, store)

    val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val messages = MutableStateFlow<List<Msg>>(emptyList())
    val contacts = MutableStateFlow<List<Contact>>(emptyList())
    val results = MutableStateFlow<List<Msg>>(emptyList())
    val blocked = MutableStateFlow(store.blocked)
    val scheduled = MutableStateFlow(store.scheduled)

    private var openThread: Long? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    init {
        app.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer)
        refresh()
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    fun refresh() {
        if (!isDefault()) return
        viewModelScope.launch {
            val (convs, msgs) = withContext(Dispatchers.IO) {
                runCatching { repo.conversations() to (openThread?.let { repo.messages(it) } ?: emptyList()) }
                    .getOrDefault(emptyList<Conversation>() to emptyList())
            }
            conversations.value = convs
            if (openThread != null) messages.value = msgs
            blocked.value = store.blocked
            scheduled.value = store.scheduled
        }
    }

    fun isDefault() = Telephony.Sms.getDefaultSmsPackage(getApplication()) == getApplication<Application>().packageName

    /** Opens (or closes, with null) the thread whose messages are shown, marking it read. */
    fun open(threadId: Long?) {
        openThread = threadId
        messages.value = emptyList()
        if (threadId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repo.markRead(threadId) }
                Notifications.cancel(getApplication(), threadId)
                refresh()
            }
        } else refresh()
    }

    fun loadContacts() {
        viewModelScope.launch { contacts.value = withContext(Dispatchers.IO) { runCatching { repo.contacts() }.getOrDefault(emptyList()) } }
    }

    fun search(q: String) {
        viewModelScope.launch { results.value = withContext(Dispatchers.IO) { runCatching { repo.search(q) }.getOrDefault(emptyList()) } }
    }

    fun send(address: String, body: String) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            Numbers.parseRecipients(address).forEach { Sender.send(app, it, body) }
            refresh()
        }
    }

    fun resend(m: Msg) {
        viewModelScope.launch(Dispatchers.IO) { Sender.resend(getApplication(), m) }
    }

    fun schedule(address: String, body: String, at: Long) {
        Scheduler.schedule(getApplication(), address, body, at)
        scheduled.value = store.scheduled
    }

    fun cancelScheduled(s: Scheduled) {
        Scheduler.cancel(getApplication(), s)
        scheduled.value = store.scheduled
    }

    fun deleteThread(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteThread(id); refresh() }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteMessage(id); refresh() }
    }

    fun togglePin(id: Long) {
        store.pinned = if (id in store.pinned) store.pinned - id else store.pinned + id
        refresh()
    }

    fun block(address: String) {
        store.block(address)
        blocked.value = store.blocked
    }

    fun unblock(address: String) {
        store.unblock(address)
        blocked.value = store.blocked
    }
}
