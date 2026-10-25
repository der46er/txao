package com.txao.music

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ComponentName
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    /** Songs that are not hidden. */
    var tracks by mutableStateOf<List<Track>>(emptyList()); private set
    private var allTracks: List<Track> = emptyList()
    private val libPrefs = LibraryPrefs(app)
    var hiddenSongIds by mutableStateOf(libPrefs.hiddenSongs); private set
    var hiddenAlbumKeys by mutableStateOf(libPrefs.hiddenAlbums); private set
    var albumSort by mutableIntStateOf(libPrefs.albumSort); private set
    var albumOrder by mutableStateOf(libPrefs.albumOrder); private set
    /** Bumped when an artist picture changes so it reloads. */
    var artistImageRev by mutableIntStateOf(0); private set
    var loaded by mutableStateOf(false); private set

    var currentId by mutableStateOf<Long?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF); private set
    var sleepEndsAt by mutableStateOf<Long?>(null); private set
    var positionMs by mutableLongStateOf(0); private set
    var durationMs by mutableLongStateOf(0); private set
    val queue = mutableStateListOf<Track>()

    /** Set by the activity: shows the system "allow this app to change the file" prompt, then runs the callback. */
    var grantHandler: ((IntentSender, () -> Unit) -> Unit)? = null

    private var byId: Map<Long, Track> = emptyMap()
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable {
        controller?.pause()
        sleepEndsAt = null
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get().also {
                it.addListener(listener)
                sync(it)
            }
        }, ContextCompat.getMainExecutor(app))
        viewModelScope.launch {
            while (true) {
                controller?.let {
                    positionMs = it.currentPosition.coerceAtLeast(0)
                    durationMs = it.duration.takeIf { d -> d > 0 } ?: 0
                }
                delay(if (isPlaying) 400 else 1000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            // Query off the main thread; large libraries would otherwise block the UI.
            val list = withContext(Dispatchers.IO) { Overrides.apply(getApplication(), MediaStoreSource.loadTracks(getApplication())) }
            allTracks = list
            byId = list.associateBy { it.id }
            applyHidden()
            loaded = true
            controller?.let { sync(it) }
        }
    }

    private fun applyHidden() {
        val hiddenAlbums = Library.albums(allTracks).filter { it.key in hiddenAlbumKeys }.flatMap { a -> a.tracks.map { it.id } }.toSet()
        tracks = allTracks.filter { it.id !in hiddenSongIds && it.id !in hiddenAlbums }
    }

    fun hideSong(t: Track) { hiddenSongIds = hiddenSongIds + t.id; libPrefs.hiddenSongs = hiddenSongIds; applyHidden() }
    fun restoreSong(id: Long) { hiddenSongIds = hiddenSongIds - id; libPrefs.hiddenSongs = hiddenSongIds; applyHidden() }
    fun hideAlbum(key: String) { hiddenAlbumKeys = hiddenAlbumKeys + key; libPrefs.hiddenAlbums = hiddenAlbumKeys; applyHidden() }
    fun restoreAlbum(key: String) { hiddenAlbumKeys = hiddenAlbumKeys - key; libPrefs.hiddenAlbums = hiddenAlbumKeys; applyHidden() }

    /** Hidden items for the settings list. */
    fun hiddenSongList(): List<Track> = allTracks.filter { it.id in hiddenSongIds }
    fun hiddenAlbumList(): List<Album> = Library.albums(allTracks).filter { it.key in hiddenAlbumKeys }

    fun setAlbumSort(mode: Int) { albumSort = mode; libPrefs.albumSort = mode }
    fun setAlbumOrder(keys: List<String>) {
        albumOrder = keys; libPrefs.albumOrder = keys
        albumSort = Library.SORT_CUSTOM; libPrefs.albumSort = Library.SORT_CUSTOM
    }

    /** Set or rename an artist's picture. [bytes] null keeps the current picture (and moves it on a rename). */
    fun updateArtistImage(oldName: String, newName: String, bytes: ByteArray?) {
        val ctx = getApplication<Application>()
        if (bytes != null) ArtistImages.save(ctx, newName, bytes) else ArtistImages.rename(ctx, oldName, newName)
        Art.clear()
        artistImageRev++
    }

    private fun sync(p: Player) {
        isPlaying = p.isPlaying
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        currentId = p.currentMediaItem?.mediaId?.toLongOrNull()
        val ids = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }
        if (queue.map { it.id.toString() } != ids) {
            queue.clear()
            ids.forEach { id -> id.toLongOrNull()?.let(byId::get)?.let { queue += it } }
        }
    }

    private fun item(t: Track) = MediaItem.Builder()
        .setMediaId(t.id.toString())
        .setUri(t.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album)
                // ArtBitmapLoader (in the service) turns this into the cover for the notification.
                .setArtworkUri(Uri.parse(t.uri))
                .build(),
        ).build()

    /** Replace the queue with [list] and start at [start], or at a random track when [shuffled]. */
    fun play(list: List<Track>, start: Track? = null, shuffled: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        val first = start ?: if (shuffled) list.random() else list.first()
        c.shuffleModeEnabled = shuffled
        c.setMediaItems(list.map(::item), list.indexOf(first).coerceAtLeast(0), 0)
        c.prepare()
        c.play()
    }

    fun playNext(t: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(t), t) else c.addMediaItem(c.currentMediaItemIndex + 1, item(t))
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNext() }
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > Library.restartThresholdMs()) c.seekTo(0) else c.seekToPrevious()
    }
    fun seekTo(ms: Long) { controller?.seekTo(ms); positionMs = ms }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
    fun removeFromQueue(index: Int) { controller?.removeMediaItem(index) }
    fun skipToQueueItem(index: Int) { controller?.let { it.seekTo(index, 0); it.play() } }

    /** Pause after [minutes]; pass 0 to cancel. */
    fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes <= 0) { sleepEndsAt = null; return }
        sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
        handler.postDelayed(sleepRunnable, minutes * 60_000L)
    }

    val current: Track? get() = currentId?.let(byId::get)

    /**
     * Apply [edit] to every track in [targets]. Write access must already be granted (Android 11+), or, on Android 10,
     * it is requested through [grantHandler] when the first file is refused. [onDone] gets a one-line summary.
     */
    fun saveEdits(targets: List<Track>, edit: TagEdit, onDone: (String) -> Unit, savedBefore: Int = 0) {
        viewModelScope.launch {
            var ok = savedBefore
            var failed = 0
            var blocked: IntentSender? = null
            var rest: List<Track> = emptyList()
            withContext(Dispatchers.IO) {
                for ((i, t) in targets.withIndex()) {
                    try {
                        MediaEditor.write(getApplication(), t, edit)
                        ok++
                    } catch (e: SecurityException) {
                        val rse = if (Build.VERSION.SDK_INT >= 29) e as? RecoverableSecurityException else null
                        if (rse != null && blocked == null) {
                            blocked = rse.userAction.actionIntent.intentSender
                            rest = targets.drop(i)
                            break
                        }
                        failed++
                    } catch (_: Exception) {
                        failed++
                    }
                }
            }
            val sender = blocked
            val handlerFn = grantHandler
            if (sender != null && handlerFn != null) {
                handlerFn(sender) { saveEdits(rest, edit, onDone, ok) }
                return@launch
            }
            Art.clear()
            refresh()
            onDone(
                when {
                    failed > 0 && ok == 0 -> "Couldn't save. Allow changes to the file and try again."
                    failed > 0 -> "Saved $ok, $failed failed"
                    else -> "Saved"
                },
            )
        }
    }

    override fun onCleared() {
        handler.removeCallbacks(sleepRunnable)
        controller?.run { removeListener(listener); release() }
        super.onCleared()
    }
}
