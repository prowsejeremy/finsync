package com.jpd.hz.playback

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jpd.hz.library.currentChapterIndex
import com.jpd.hz.library.skipTarget
import kotlin.random.Random

private const val US_PER_MS = 1_000L
// How often a loaded book checks for a new chapter, so the notification's title follows it.
private const val CHAPTER_CHECK_MS = 1_000L

/** Where the current book is, for book_progress (3b). [ended] means it played to the end. */
data class BookPosition(val bookId: String, val positionMs: Long, val ended: Boolean)

/**
 * The session's player. Owns the queue, play order, repeat and shuffle, and drives [BassEngine].
 *
 * SimpleBasePlayer 1.4.1 has no shuffle order, so while shuffle is on the playlist it reports is
 * already in play order. Media3's own next and previous handling (including "previous restarts
 * the track after 3 s"), the notification and the queue sheet then all follow play order.
 * Every handler finishes synchronously on the main thread.
 *
 * A book (3b) is one queue item with its chapters in its extras. While one is current it plays
 * through a tempo stream at [bookSpeed], repeat counts as off, the system's previous and next
 * skip 15 s back and 30 s on, and the reported title is the current chapter.
 */
@OptIn(UnstableApi::class)
class BassPlayer(
    looper: Looper,
    private val engine: BassEngine,
    private val random: Random = Random.Default
) : SimpleBasePlayer(looper) {

    private class Entry(val uid: Long, val item: MediaItem)

    /** The speed every book plays at; the service loads it before anything plays. */
    var bookSpeed: Float = DEFAULT_BOOK_SPEED
    /** Called when a controller picks a book speed, so the service can keep it. */
    var onBookSpeedChanged: ((Float) -> Unit)? = null

    private var entries: List<Entry> = emptyList()      // album order
    private var order: List<Int> = emptyList()          // play order: indices into entries
    private var current = 0                             // position in order
    private var queued = C.INDEX_UNSET                  // position queued in the engine
    private var loaded = false                          // engine holds the current track
    private var idlePositionMs = 0L                     // position while not loaded
    private var playedToEnd = false                     // ENDED because the item played out
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var shuffle = false
    private var playerError: PlaybackException? = null
    private var pendingAutoTransition = false
    private var nextUid = 0L
    private var reportedChapter = C.INDEX_UNSET         // chapter in the title; unset for music
    private val chapterHandler = Handler(looper)
    private val chapterCheck = object : Runnable {
        override fun run() {
            if (loaded && currentChapterOrUnset() != reportedChapter) invalidateState()
            chapterHandler.postDelayed(this, CHAPTER_CHECK_MS)
        }
    }

    init {
        engine.onTrackEnded = ::onTrackEnded
        chapterHandler.postDelayed(chapterCheck, CHAPTER_CHECK_MS)
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

    /** The current book's place for book_progress; null unless a book is current (3b). */
    fun bookPosition(): BookPosition? {
        val item = currentItem()?.takeIf { it.isBook() } ?: return null
        val ended = playbackState == Player.STATE_ENDED
        // Ended without playing out means the file wouldn't load: there's no place to save.
        if (ended && !playedToEnd) return null
        return BookPosition(
            bookId = item.mediaId,
            positionMs = currentPositionMs(),
            ended = ended
        )
    }

    override fun getState(): State {
        reportedChapter = currentChapterOrUnset()
        val builder = State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (order.isEmpty()) Player.STATE_IDLE else playbackState)
            .setPlayerError(playerError)
            .setRepeatMode(effectiveRepeatMode())
            .setShuffleModeEnabled(shuffle)
            .setPlaybackParameters(currentPlaybackParameters())
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
        chapterHandler.removeCallbacks(chapterCheck)
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

    // One speed for every book, kept by the service; music always plays at 1.0× (spec "Speed").
    override fun handleSetPlaybackParameters(
        playbackParameters: PlaybackParameters
    ): ListenableFuture<*> {
        bookSpeed = nearestBookSpeed(playbackParameters.speed)
        onBookSpeedChanged?.invoke(bookSpeed)
        if (loaded && isBookCurrent()) engine.setSpeed(bookSpeed)
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
        // While a book plays, the system's previous and next skip instead (decision 12). Media3
        // routes them here with these commands, even when there's nothing to skip to.
        val skipMs = if (isBookCurrent()) systemSkipMs(seekCommand) else null
        if (skipMs != null) {
            moveTo(current, skipTarget(currentPositionMs(), skipMs, currentDurationMs()))
        } else {
            val position = if (positionMs == C.TIME_UNSET) 0L else positionMs
            moveTo(mediaItemIndex.coerceIn(0, order.size - 1), position)
        }
        return Futures.immediateVoidFuture()
    }

    // The catalogue's duration, or the engine's for the loaded track when the catalogue has none.
    // A current book reports its chapter as the title (spec "Labels", decision 11).
    private fun itemData(entry: Entry, position: Int): MediaItemData {
        val extras = entry.item.mediaMetadata.extras
        val catalogueMs = extras?.getLong(TrackExtras.DURATION_MS, 0L) ?: 0L
        val useEngine = catalogueMs <= 0 && loaded && position == current
        val durationMs = if (useEngine) engine.durationMs() else catalogueMs
        val builder = MediaItemData.Builder(entry.uid)
            .setMediaItem(entry.item)
            .setDurationUs(if (durationMs > 0) durationMs * US_PER_MS else C.TIME_UNSET)
            .setIsSeekable(true)
        if (position == current && reportedChapter != C.INDEX_UNSET) {
            builder.setMediaMetadata(entry.item.chapterMetadata(reportedChapter))
        }
        return builder.build()
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
        playedToEnd = false
        var position = current
        var startMs = positionMs
        repeat(order.size) {
            val path = pathAt(position)
            if (path != null && engine.load(path, startMs, speedAt(position))) {
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
        var candidate = QueueOrder.autoNextIndex(current, order.size, effectiveRepeatMode())
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
            playedToEnd = true
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

    // Seeking inside the loaded item moves its stream instead of reopening the file (decision
    // 15); a refused seek, another item or an ended queue loads as before.
    private fun moveTo(position: Int, positionMs: Long) {
        val sameItem = position == current
        current = position
        when {
            playbackState == Player.STATE_IDLE -> idlePositionMs = positionMs
            sameItem && loaded && engine.seek(positionMs) -> Unit
            else -> loadCurrent(positionMs)
        }
    }

    private fun currentItem(): MediaItem? =
        if (order.isEmpty()) null else entries[order[current]].item

    private fun isBookCurrent(): Boolean = currentItem()?.isBook() == true

    // A book counts as repeat off, so it ends rather than loops; music keeps the user's setting.
    private fun effectiveRepeatMode(): Int =
        if (isBookCurrent()) Player.REPEAT_MODE_OFF else repeatMode

    private fun currentPlaybackParameters(): PlaybackParameters =
        if (isBookCurrent()) PlaybackParameters(bookSpeed) else PlaybackParameters.DEFAULT

    // A book loads into a tempo stream at the book speed; music plays as decoded.
    private fun speedAt(position: Int): Float? =
        if (entries[order[position]].item.isBook()) bookSpeed else null

    private fun currentChapterOrUnset(): Int {
        val item = currentItem()?.takeIf { it.isBook() } ?: return C.INDEX_UNSET
        return currentChapterIndex(item.bookChapters().map { it.startMs }, currentPositionMs())
    }

    // For clamping skips: the catalogue's length, else the engine's (0, no end, when unknown).
    private fun currentDurationMs(): Long {
        val extras = currentItem()?.mediaMetadata?.extras
        val catalogueMs = extras?.getLong(TrackExtras.DURATION_MS, 0L) ?: 0L
        return if (catalogueMs > 0) catalogueMs else engine.durationMs()
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
                Player.COMMAND_SET_SPEED_AND_PITCH,
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
