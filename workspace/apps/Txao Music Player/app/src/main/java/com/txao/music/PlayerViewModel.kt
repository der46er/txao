package com.txao.music

import android.app.Application
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    var tracks by mutableStateOf<List<Track>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set

    var currentId by mutableStateOf<Long?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF); private set
    var sleepEndsAt by mutableStateOf<Long?>(null); private set
    val queue = mutableStateListOf<Track>()

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
        }, androidx.core.content.ContextCompat.getMainExecutor(app))
    }

    fun refresh() {
        viewModelScope.launch {
            // Query off the main thread; large libraries would otherwise block the UI.
            tracks = withContext(Dispatchers.IO) { MediaStoreSource.loadTracks(getApplication()) }
            loaded = true
        }
    }

    private fun sync(p: Player) {
        isPlaying = p.isPlaying
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        currentId = p.currentMediaItem?.mediaId?.toLongOrNull()
        if (queue.size != p.mediaItemCount || queue.map { it.id.toString() } != (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }) {
            val byId = tracks.associateBy { it.id.toString() }
            queue.clear()
            for (i in 0 until p.mediaItemCount) byId[p.getMediaItemAt(i).mediaId]?.let { queue += it }
        }
    }

    private fun item(t: Track) = MediaItem.Builder()
        .setMediaId(t.id.toString())
        .setUri(t.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album)
                .setArtworkUri(MediaStoreSource.albumArtUri(t.albumId))
                .build(),
        ).build()

    /** Replace the queue with [list] and start at [start]. */
    fun play(list: List<Track>, start: Track) {
        val c = controller ?: return
        c.setMediaItems(list.map(::item), list.indexOf(start).coerceAtLeast(0), 0)
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
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
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
    fun position(): Long = controller?.currentPosition ?: 0
    fun duration(): Long = controller?.duration?.takeIf { it > 0 } ?: 0

    /** Pause after [minutes]; pass 0 to cancel. */
    fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes <= 0) { sleepEndsAt = null; return }
        sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
        handler.postDelayed(sleepRunnable, minutes * 60_000L)
    }

    val current: Track? get() = tracks.firstOrNull { it.id == currentId }

    override fun onCleared() {
        handler.removeCallbacks(sleepRunnable)
        controller?.run { removeListener(listener); release() }
        super.onCleared()
    }
}
