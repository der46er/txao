package com.txao.core

import org.json.JSONObject

/** A locally changed item waiting to be pushed. [json] is the plaintext payload; it must contain "t" (type). */
class DirtyItem(val id: String, val updatedAt: Long, val json: JSONObject)

/**
 * What an app implements so the shared [ItemSyncEngine] can sync its data (see SYNC-SPEC.md).
 * Items are small JSON documents with a stable id, a type `t` and an `updatedAt` (epoch ms).
 * Deletes are tombstones: `{"t":"del","id":"<itemId>","updatedAt":ms}`.
 */
interface SyncStore {
    /** Last server sequence number seen. Persist it. */
    var serverSeq: Long

    /** Items changed (or deleted, as tombstones) since they were last pushed. */
    fun dirtyItems(): List<DirtyItem>
    fun markClean(id: String, updatedAt: Long)

    /**
     * Apply a decrypted remote item. Resolve conflicts here (newest [updatedAt] wins; use [lastWriterWins]).
     * If the local copy is newer, mark it dirty so it is pushed back. Return true if local data changed.
     */
    fun applyRemote(id: String, updatedAt: Long, json: JSONObject): Boolean

    // ---- optional binary attachments (pictures, thumbnails). Encrypted with AAD "blob:<id>". ----
    fun blobsToUpload(): List<String> = emptyList()
    fun readBlob(id: String): ByteArray? = null
    fun markBlobUploaded(id: String) {}
    fun blobsMissing(): List<String> = emptyList()
    fun writeBlob(id: String, bytes: ByteArray) {}
}

/** True if the remote copy should replace the local one. A tie keeps the local copy. */
fun lastWriterWins(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean = localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt

class SyncReport(val pulled: Int, val pushed: Int, val skipped: Int)

/** Pull, push, then blobs. Never throws for a single bad item: unreadable items are counted and skipped. */
class ItemSyncEngine(
    private val store: SyncStore,
    private val keys: TxaoCrypto.Keys,
    private val transport: Transport,
) {
    fun syncOnce(): SyncReport {
        var pulled = 0
        var skipped = 0

        while (true) {
            val resp = transport.changes(store.serverSeq)
            for (item in resp.items) {
                try {
                    val plain = TxaoCrypto.decrypt(keys, item.ciphertext, item.id)
                    if (store.applyRemote(item.id, item.updatedAt, JSONObject(String(plain)))) pulled++
                } catch (e: Exception) {
                    skipped++ // wrong key or corrupted: never crash, never overwrite
                }
            }
            store.serverSeq = resp.seq
            if (!resp.more) break
        }

        var pushed = 0
        for (blobId in store.blobsToUpload()) {
            val bytes = store.readBlob(blobId) ?: continue
            if (!transport.hasBlob(blobId)) transport.putBlob(blobId, TxaoCrypto.encrypt(keys, bytes, "blob:$blobId"))
            store.markBlobUploaded(blobId)
        }
        for (item in store.dirtyItems()) {
            val cipher = TxaoCrypto.encrypt(keys, item.json.toString().toByteArray(), item.id)
            transport.putItem(item.id, item.updatedAt, cipher) // false = server has newer; the next pull applies it
            store.markClean(item.id, item.updatedAt)
            pushed++
        }

        for (blobId in store.blobsMissing()) {
            val enc = transport.getBlob(blobId) ?: continue
            try { store.writeBlob(blobId, TxaoCrypto.decrypt(keys, enc, "blob:$blobId")) } catch (_: Exception) { skipped++ }
        }
        return SyncReport(pulled, pushed, skipped)
    }
}
