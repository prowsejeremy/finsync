package com.jpd.finsync.ui

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.jpd.finsync.R
import com.jpd.finsync.library.LibraryRepository
import com.jpd.finsync.playback.QueueOrder
import com.jpd.finsync.playback.TrackExtras
import com.jpd.finsync.playback.TrackResolver
import com.jpd.finsync.playback.remapStartIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val POSITION_POLL_MS = 500L
private const val MESSAGE_BUFFER = 4

/** The current track's technical details, for the Player's info row. */
data class TrackInfo(
    val codec: String? = null,
    val bitDepth: Int? = null,
    val sampleRate: Int? = null,
    val bitrate: Int? = null,
    val sizeBytes: Long? = null
)

data class QueueRow(val title: String, val artist: String, val durationMs: Long?)

/** What the mini-player, Player, Queue sheet and album detail show about playback. */
data class PlaybackUiState(
    val hasQueue: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val albumTitle: String = "",
    val albumId: String? = null,
    val artworkPath: String? = null,
    val info: TrackInfo = TrackInfo(),
    val isPlaying: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    /** In play order. */
    val queue: List<QueueRow> = emptyList(),
    val currentIndex: Int = 0
)

data class PlaybackPosition(val positionMs: Long = 0L, val durationMs: Long = 0L)

/**
 * Activity-scoped mirror of the one MediaController that MainActivity builds in onStart. Screens
 * read playback here and send commands through it; nothing else talks to the service.
 */
class PlaybackViewModel(app: Application) : AndroidViewModel(app) {

    private val resolver = TrackResolver(LibraryRepository(app))
    private var controller: MediaController? = null
    private var positionPoller: Job? = null

    private val _state = MutableLiveData(PlaybackUiState())
    val state: LiveData<PlaybackUiState> = _state

    private val _position = MutableLiveData(PlaybackPosition())
    val position: LiveData<PlaybackPosition> = _position

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = MESSAGE_BUFFER)
    /** String resource IDs to show as toasts. */
    val messages: SharedFlow<Int> = _messages

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)

        override fun onPlayerError(error: PlaybackException) {
            _messages.tryEmit(R.string.playback_unavailable)
        }
    }

    fun attach(mediaController: MediaController) {
        detach()
        controller = mediaController
        mediaController.addListener(listener)
        publish(mediaController)
        positionPoller = viewModelScope.launch {
            while (isActive) {
                publishPosition(mediaController)
                delay(POSITION_POLL_MS)
            }
        }
    }

    fun detach() {
        positionPoller?.cancel()
        positionPoller = null
        controller?.removeListener(listener)
        controller = null
    }

    override fun onCleared() {
        detach()
    }

    /**
     * Plays any list of downloaded tracks ([itemIds] in list order): an album, All songs or
     * Songs. Play and row taps pass shuffle off; Shuffle starts at a random track with shuffle on.
     */
    fun playTracks(itemIds: List<String>, startIndex: Int, shuffle: Boolean) {
        viewModelScope.launch {
            val playable = resolver.playableIds(itemIds)
            val kept = itemIds.map { it in playable }
            val keptCount = kept.count { it }
            val start = if (shuffle && keptCount > 0) {
                Random.nextInt(keptCount)
            } else {
                remapStartIndex(kept, startIndex)
            }
            val mediaController = controller
            when {
                start == null -> _messages.tryEmit(R.string.files_missing)
                mediaController == null -> _messages.tryEmit(R.string.playback_unavailable)
                else -> {
                    val items = itemIds.filter { it in playable }
                        .map { MediaItem.Builder().setMediaId(it).build() }
                    mediaController.shuffleModeEnabled = shuffle
                    mediaController.setMediaItems(items, start, 0L)
                    mediaController.prepare()
                    mediaController.play()
                }
            }
        }
    }

    fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) {
            mediaController.pause()
            return
        }
        when (mediaController.playbackState) {
            Player.STATE_ENDED -> mediaController.seekToDefaultPosition()
            Player.STATE_IDLE -> mediaController.prepare()
        }
        mediaController.play()
    }

    fun next() {
        controller?.seekToNext()
    }

    /** Media3 restarts the track instead when more than 3 s have played. */
    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun cycleRepeatMode() {
        controller?.let { it.repeatMode = QueueOrder.nextRepeatMode(it.repeatMode) }
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** [queueIndex] is in play order, as listed by [PlaybackUiState.queue]. */
    fun jumpTo(queueIndex: Int) {
        val mediaController = controller ?: return
        mediaController.seekToDefaultPosition(queueIndex)
        mediaController.play()
    }

    /** Logout: stop and empty the queue; the service then clears its saved copy. */
    fun clearQueue() {
        val mediaController = controller ?: return
        mediaController.stop()
        mediaController.clearMediaItems()
    }

    private fun publish(player: Player) {
        val metadata = player.mediaMetadata
        val extras = metadata.extras
        _state.value = PlaybackUiState(
            hasQueue = player.mediaItemCount > 0,
            mediaId = player.currentMediaItem?.mediaId,
            title = metadata.title?.toString() ?: "",
            artist = metadata.artist?.toString() ?: "",
            albumTitle = metadata.albumTitle?.toString() ?: "",
            albumId = extras?.getString(TrackExtras.ALBUM_ID),
            artworkPath = metadata.artworkUri?.path,
            info = TrackInfo(
                codec = extras?.getString(TrackExtras.CODEC),
                bitDepth = extras?.intOrNull(TrackExtras.BIT_DEPTH),
                sampleRate = extras?.intOrNull(TrackExtras.SAMPLE_RATE),
                bitrate = extras?.intOrNull(TrackExtras.BITRATE),
                sizeBytes = extras?.longOrNull(TrackExtras.FILE_SIZE)
            ),
            isPlaying = player.isPlaying,
            repeatMode = player.repeatMode,
            shuffle = player.shuffleModeEnabled,
            queue = (0 until player.mediaItemCount).map { queueRow(player.getMediaItemAt(it)) },
            currentIndex = player.currentMediaItemIndex
        )
        publishPosition(player)
    }

    private fun queueRow(item: MediaItem): QueueRow {
        val metadata = item.mediaMetadata
        return QueueRow(
            title = metadata.title?.toString() ?: "",
            artist = metadata.artist?.toString() ?: "",
            durationMs = metadata.extras?.longOrNull(TrackExtras.DURATION_MS)
        )
    }

    private fun publishPosition(player: Player) {
        val duration = player.duration
        _position.value = PlaybackPosition(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = if (duration == C.TIME_UNSET) 0L else duration
        )
    }
}

private fun Bundle.intOrNull(key: String): Int? = if (containsKey(key)) getInt(key) else null

private fun Bundle.longOrNull(key: String): Long? = if (containsKey(key)) getLong(key) else null
