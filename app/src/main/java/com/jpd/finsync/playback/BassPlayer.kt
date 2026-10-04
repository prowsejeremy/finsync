package com.jpd.finsync.playback

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.random.Random

private const val US_PER_MS = 1_000L

/**
 * The session's player. Owns the queue, play order, repeat and shuffle, and drives [BassEngine].
 *
 * SimpleBasePlayer 1.4.1 has no shuffle order, so while shuffle is on the playlist it reports is
 * already in play order. Media3's own next and previous handling (including "previous restarts
 * the track after 3 s"), the notification and the queue sheet then all follow play order.
 * Every handler finishes synchronously on the main thread.
 */
@OptIn(UnstableApi::class)
class BassPlayer(
    looper: Looper,
    private val engine: BassEngine,
    private val random: Random = Random.Default
) : SimpleBasePlayer(looper) {

    private class Entry(val uid: Long, val item: MediaItem)

    private var entries: List<Entry> = emptyList()      // album order
    private var order: List<Int> = emptyList()          // play order: indices into entries
    private var current = 0                             // position in order
    private var queued = C.INDEX_UNSET                  // position queued in the engine
    private var loaded = false                          // engine holds the current track
    private var idlePositionMs = 0L                     // position while not loaded
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var shuffle = false
    private var playerError: PlaybackException? = null
    private var pendingAutoTransition = false
    private var nextUid = 0L

    init {
        engine.onTrackEnded = ::onTrackEnded
    }

    /** What ResumeStore saves; null when the queue is empty. */
    fun resumeState(): ResumeState? {
        if (order.isEmpty()) return null
        return ResumeState(
            itemIds = entries.map { it.item.mediaId },
            index = order[current],
            positionMs = currentPositionMs(),
            repeatMode = repeatMode,
            shuffle = shuffle
        )
    }

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (order.isEmpty()) Player.STATE_IDLE else playbackState)
            .setPlayerError(playerError)
            .setRepeatMode(repeatMode)
            .setShuffleModeEnabled(shuffle)
            .setPlaylist(order.mapIndexed { position, entry -> itemData(entries[entry], position) })
            .setContentPositionMs(PositionSupplier { currentPositionMs() })
        if (order.isNotEmpty()) builder.setCurrentMediaItemIndex(current)
        if (pendingAutoTransition) {
            // Reported once, so Media3 sees an automatic transition rather than a seek.
            pendingAutoTransition = false
            builder.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, 0)
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        if (loaded) {
            if (playWhenReady) engine.play() else engine.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (order.isNotEmpty() && playbackState == Player.STATE_IDLE) loadCurrent(idlePositionMs)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        idlePositionMs = currentPositionMs()
        engine.stop()
        loaded = false
        queued = C.INDEX_UNSET
        playbackState = Player.STATE_IDLE
        playerError = null
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        engine.release()
        loaded = false
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        this.repeatMode = repeatMode
        if (loaded) queueNext()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        if (shuffleModeEnabled == shuffle) return Futures.immediateVoidFuture()
        shuffle = shuffleModeEnabled
        if (order.isNotEmpty()) {
            // The playing track carries on; only what follows it changes.
            if (shuffle) {
                order = QueueOrder.shuffledOrder(entries.size, order[current], random)
                current = 0
            } else {
                current = QueueOrder.positionAfterShuffleOff(order, current)
                order = QueueOrder.albumOrder(entries.size)
            }
            if (loaded) queueNext()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        entries = mediaItems.map { Entry(nextUid++, it) }
        val lastIndex = (entries.size - 1).coerceAtLeast(0)
        val start = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, lastIndex)
        if (shuffle) {
            order = QueueOrder.shuffledOrder(entries.size, start, random)
            current = 0
        } else {
            order = QueueOrder.albumOrder(entries.size)
            current = start
        }
        playerError = null
        val positionMs = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs
        when {
            order.isEmpty() -> clearEngine()
            playbackState == Player.STATE_IDLE -> idlePositionMs = positionMs
            else -> loadCurrent(positionMs)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<*> {
        val firstNewEntry = entries.size
        entries = entries + mediaItems.map { Entry(nextUid++, it) }
        val position = index.coerceIn(0, order.size)
        val wasEmpty = order.isEmpty()
        val newEntries = mediaItems.indices.map { firstNewEntry + it }
        order = QueueOrder.insert(order, position, newEntries)
        if (!wasEmpty && position <= current) current += mediaItems.size
        if (loaded) queueNext()
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val to = toIndex.coerceAtMost(order.size)
        if (fromIndex >= to) return Futures.immediateVoidFuture()
        val removedEntries = order.subList(fromIndex, to).toSet()
        val currentRemoved = current in fromIndex until to
        order = QueueOrder.removeRange(order, fromIndex, to)
        entries = entries.filterIndexed { entryIndex, _ -> entryIndex !in removedEntries }
        current = QueueOrder.currentAfterRemove(current, fromIndex, to, order.size)
        when {
            order.isEmpty() -> clearEngine()
            currentRemoved && loaded -> loadCurrent(0L)
            loaded -> queueNext()
        }
        return Futures.immediateVoidFuture()
    }

    // Queue editing is out of scope (spec). Completing without a change makes controllers
    // re-read the unchanged state.
    override fun handleMoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int
    ): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        if (order.isEmpty()) return Futures.immediateVoidFuture()
        current = mediaItemIndex.coerceIn(0, order.size - 1)
        val position = if (positionMs == C.TIME_UNSET) 0L else positionMs
        if (playbackState == Player.STATE_IDLE) idlePositionMs = position else loadCurrent(position)
        return Futures.immediateVoidFuture()
    }

    // The catalogue's duration, or the engine's for the loaded track when the catalogue has none.
    private fun itemData(entry: Entry, position: Int): MediaItemData {
        val extras = entry.item.mediaMetadata.extras
        val catalogueMs = extras?.getLong(TrackExtras.DURATION_MS, 0L) ?: 0L
        val useEngine = catalogueMs <= 0 && loaded && position == current
        val durationMs = if (useEngine) engine.durationMs() else catalogueMs
        return MediaItemData.Builder(entry.uid)
            .setMediaItem(entry.item)
            .setDurationUs(if (durationMs > 0) durationMs * US_PER_MS else C.TIME_UNSET)
            .setIsSeekable(true)
            .build()
    }

    private fun currentPositionMs(): Long = if (loaded) engine.positionMs() else idlePositionMs

    private fun pathAt(position: Int): String? =
        entries[order[position]].item.localConfiguration?.uri?.path

    /** Loads the current track (skipping any that can't be decoded) and queues the next. */
    private fun loadCurrent(positionMs: Long) {
        if (!engine.initialise()) {
            playerError = PlaybackException(
                "Playback unavailable", null, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
            )
            loaded = false
            playbackState = Player.STATE_IDLE
            return
        }
        playerError = null
        var position = current
        var startMs = positionMs
        repeat(order.size) {
            val path = pathAt(position)
            if (path != null && engine.load(path, startMs)) {
                current = position
                loaded = true
                playbackState = Player.STATE_READY
                if (playWhenReady) engine.play() else engine.pause()
                queueNext()
                return
            }
            // A decode error skips the track, without wrapping round, so a bad queue ends.
            position = QueueOrder.autoNextIndex(position, order.size, Player.REPEAT_MODE_OFF)
                ?: return finish()
            startMs = 0L
        }
        finish()
    }

    /** Queues the track that follows the current one, skipping any that can't be decoded. */
    private fun queueNext() {
        queued = C.INDEX_UNSET
        var candidate = QueueOrder.autoNextIndex(current, order.size, repeatMode)
        repeat(order.size) {
            val position = candidate ?: return@repeat
            val path = pathAt(position)
            if (path != null && engine.queueNext(path)) {
                queued = position
                return
            }
            candidate = QueueOrder.autoNextIndex(position, order.size, Player.REPEAT_MODE_OFF)
        }
        engine.queueNext(null)
    }

    private fun onTrackEnded(nextStarted: Boolean) {
        if (nextStarted && queued != C.INDEX_UNSET) {
            current = queued
            pendingAutoTransition = true
            queueNext()
        } else {
            finish()
        }
        invalidateState()
    }

    /** The queue has played out, or nothing left in it could be decoded. */
    private fun finish() {
        idlePositionMs = engine.positionMs()
        loaded = false
        queued = C.INDEX_UNSET
        playbackState = Player.STATE_ENDED
    }

    private fun clearEngine() {
        engine.stop()
        loaded = false
        queued = C.INDEX_UNSET
        idlePositionMs = 0L
        current = 0
        playbackState = Player.STATE_IDLE
    }

    private companion object {
        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_RELEASE,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS
            )
            .build()
    }
}
