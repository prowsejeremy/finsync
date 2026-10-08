package com.jpd.hz.ui

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.jpd.hz.R
import com.jpd.hz.library.BookRepository
import com.jpd.hz.library.Chapter
import com.jpd.hz.library.LibraryRepository
import com.jpd.hz.library.currentChapterIndex
import com.jpd.hz.library.nextChapterTarget
import com.jpd.hz.library.previousChapterTarget
import com.jpd.hz.library.resumePositionMs
import com.jpd.hz.library.skipTarget
import com.jpd.hz.playback.QueueOrder
import com.jpd.hz.playback.SKIP_BACK_MS
import com.jpd.hz.playback.SKIP_FORWARD_MS
import com.jpd.hz.playback.TrackExtras
import com.jpd.hz.playback.TrackResolver
import com.jpd.hz.playback.bookChapters
import com.jpd.hz.playback.isBook
import com.jpd.hz.playback.remapStartIndex
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

/**
 * The playing book (3b), for the Player, its sheets and the mini-player. Screens read chapters
 * here and move between them only through [PlaybackViewModel]'s chapter methods (spec "The
 * chapter seam"), so playing chapters as separate items later wouldn't change any screen.
 */
data class BookPlayback(
    val bookId: String,
    val title: String,
    val author: String?,
    /** Never empty: a book without chapters is one chapter named after it. */
    val chapters: List<Chapter>,
    val chapterIndex: Int,
    /** The book's length from the catalogue, or the player's when the catalogue has none. */
    val durationMs: Long,
    val speed: Float
) {
    val chapterStartsMs: List<Long> get() = chapters.map { it.startMs }
}

/** What the mini-player, Player, Queue sheet and album detail show about playback. */
data class PlaybackUiState(
    val hasQueue: Boolean = false,
    val mediaId: String? = null,
    /** For a book, the current chapter's name. */
    val title: String = "",
    /** For a book, the book's title. */
    val artist: String = "",
    /** The Player's artist pill: the album artist, else the track's artists. Music only. */
    val albumArtist: String = "",
    /** The album's first album artist, whose page the pill opens. Music only. */
    val albumArtistId: String? = null,
    val albumTitle: String = "",
    val albumId: String? = null,
    val artworkPath: String? = null,
    val info: TrackInfo = TrackInfo(),
    val isPlaying: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    /** In play order. */
    val queue: List<QueueRow> = emptyList(),
    val currentIndex: Int = 0,
    /** Set while a book plays; null for music. */
    val book: BookPlayback? = null
)

data class PlaybackPosition(val positionMs: Long = 0L, val durationMs: Long = 0L)

/**
 * Activity-scoped mirror of the one MediaController that MainActivity builds in onStart. Screens
 * read playback here and send commands through it; nothing else talks to the service.
 */
class PlaybackViewModel(app: Application) : AndroidViewModel(app) {

    private val books = BookRepository(app)
    private val resolver = TrackResolver(LibraryRepository(app), books)
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
                // A new chapter changes the title line, so the whole state is republished (3b).
                if (chapterChanged(mediaController)) {
                    publish(mediaController)
                } else {
                    publishPosition(mediaController)
                }
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

    /**
     * The book page's Resume, or Play when the book is new or finished (spec "Progress"). When
     * the book is already the queue it carries on from where it is, not from the last save.
     */
    fun playBook(bookId: String) {
        viewModelScope.launch {
            val progress = books.bookProgress(bookId)
            if (isCurrentItem(bookId)) {
                // A finished book restored at startup sits near its end, not ENDED: start over.
                continueCurrent(startMs = if (progress?.finished == true) 0L else null)
            } else {
                startBook(bookId, resumePositionMs(progress))
            }
        }
    }

    /** Start over: from 0. The next progress save clears Finished (spec). */
    fun startBookOver(bookId: String) = playBookAt(bookId, 0L)

    /** A chapter tapped on the book page: the book plays from that chapter's start. */
    fun playBookFrom(bookId: String, startMs: Long) = playBookAt(bookId, startMs)

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

    /** During a book the player turns this into +30 s, as for the system's next (3b). */
    fun next() {
        controller?.seekToNext()
    }

    /** Media3 restarts the track instead when more than 3 s have played. */
    fun previous() {
        controller?.seekToPrevious()
    }

    // The mini-player's moves (3b refinements): its Next button, its swipes and their TalkBack
    // actions; also the Player's swipe. The Player's buttons, the notification and headset
    // buttons keep their own rules.

    /** The next chapter while a book plays (nothing on the last), else the next track. */
    fun nextTrackOrChapter() {
        if (_state.value?.book != null) nextChapter() else next()
    }

    /** The previous chapter while a book plays (this one's start after 3 s), else previous(). */
    fun previousTrackOrChapter() {
        if (_state.value?.book != null) previousChapter() else previous()
    }

    /**
     * Whether a swipe left would move: a book has a next chapter, or the queue a next item.
     * Media3's answer follows repeat and shuffle, and BassPlayer reports its queue in play order.
     */
    fun canGoNext(): Boolean {
        val mediaController = controller ?: return false
        val book = _state.value?.book ?: return mediaController.hasNextMediaItem()
        return nextChapterTarget(book.chapterStartsMs, mediaController.currentPosition) != null
    }

    /** Previous always does something once connected: it restarts or goes back. */
    fun canGoPrevious(): Boolean = controller != null

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

    // The chapter seam (3b): screens never seek to chapters themselves.

    /** Next chapter's start; on the last chapter it does nothing (spec "Next chapter"). */
    fun nextChapter() {
        val mediaController = controller ?: return
        val book = _state.value?.book ?: return
        nextChapterTarget(book.chapterStartsMs, mediaController.currentPosition)
            ?.let { mediaController.seekTo(it) }
    }

    /** This chapter's start once more than 3 s of it has played, else the previous chapter's. */
    fun previousChapter() {
        val mediaController = controller ?: return
        val book = _state.value?.book ?: return
        val target = previousChapterTarget(book.chapterStartsMs, mediaController.currentPosition)
        mediaController.seekTo(target)
    }

    /** The Chapters sheet's tap; playing or paused stays as it is. */
    fun jumpToChapter(index: Int) {
        val mediaController = controller ?: return
        val chapter = _state.value?.book?.chapters?.getOrNull(index) ?: return
        mediaController.seekTo(chapter.startMs)
    }

    /** −15 s, kept within the book (spec "Skip"). */
    fun skipBack() = skipBy(-SKIP_BACK_MS)

    /** +30 s, kept within the book (spec "Skip"). */
    fun skipForward() = skipBy(SKIP_FORWARD_MS)

    /** One of the six steps; the player keeps it for every book (spec "Speed"). */
    fun setSpeed(speed: Float) {
        controller?.setPlaybackParameters(PlaybackParameters(speed))
    }

    private fun skipBy(deltaMs: Long) {
        val mediaController = controller ?: return
        val book = _state.value?.book ?: return
        mediaController.seekTo(skipTarget(mediaController.currentPosition, deltaMs, book.durationMs))
    }

    private fun playBookAt(bookId: String, startMs: Long) {
        viewModelScope.launch {
            if (isCurrentItem(bookId)) continueCurrent(startMs) else startBook(bookId, startMs)
        }
    }

    // A null start keeps the live position, and a finished book starts over (spec "Finishing").
    private fun continueCurrent(startMs: Long?) {
        val mediaController = controller ?: return
        when {
            startMs != null -> mediaController.seekTo(startMs)
            mediaController.playbackState == Player.STATE_ENDED -> mediaController.seekTo(0L)
        }
        if (mediaController.playbackState == Player.STATE_IDLE) mediaController.prepare()
        mediaController.play()
    }

    // A book plays alone (spec "Queue"); shuffle means nothing for one item, so it goes off.
    private suspend fun startBook(bookId: String, startMs: Long) {
        val playable = resolver.playableIds(listOf(bookId))
        val mediaController = controller
        when {
            bookId !in playable -> _messages.tryEmit(R.string.files_missing)
            mediaController == null -> _messages.tryEmit(R.string.playback_unavailable)
            else -> {
                val item = MediaItem.Builder().setMediaId(bookId).build()
                mediaController.shuffleModeEnabled = false
                mediaController.setMediaItems(listOf(item), 0, startMs)
                mediaController.prepare()
                mediaController.play()
            }
        }
    }

    private fun isCurrentItem(itemId: String): Boolean =
        controller?.currentMediaItem?.mediaId == itemId

    private fun publish(player: Player) {
        val metadata = player.mediaMetadata
        val extras = metadata.extras
        val book = bookPlaybackOf(player)
        _state.value = PlaybackUiState(
            hasQueue = player.mediaItemCount > 0,
            mediaId = player.currentMediaItem?.mediaId,
            // A book's title line is its chapter, over the book's title (spec "Labels"), so the
            // mini-player shows both with no change of its own.
            title = book?.let { it.chapters[it.chapterIndex].name }
                ?: metadata.title?.toString() ?: "",
            artist = book?.title ?: metadata.artist?.toString() ?: "",
            albumArtist = metadata.albumArtist?.toString() ?: metadata.artist?.toString() ?: "",
            albumArtistId =
                if (book == null) extras?.getString(TrackExtras.ALBUM_ARTIST_ID) else null,
            albumTitle = book?.title ?: metadata.albumTitle?.toString() ?: "",
            albumId = if (book == null) extras?.getString(TrackExtras.ALBUM_ID) else null,
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
            currentIndex = player.currentMediaItemIndex,
            book = book
        )
        publishPosition(player)
    }

    // Read from the queue item's own extras; the player's metadata names the chapter instead.
    private fun bookPlaybackOf(player: Player): BookPlayback? {
        val item = player.currentMediaItem?.takeIf { it.isBook() } ?: return null
        val chapters = item.bookChapters()
        val extras = item.mediaMetadata.extras
        val catalogueMs = extras?.longOrNull(TrackExtras.DURATION_MS) ?: 0L
        val playerMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        return BookPlayback(
            bookId = item.mediaId,
            title = item.mediaMetadata.title?.toString() ?: "",
            author = extras?.getString(TrackExtras.BOOK_AUTHOR),
            chapters = chapters,
            chapterIndex = currentChapterIndex(chapters.map { it.startMs }, player.currentPosition),
            durationMs = if (catalogueMs > 0) catalogueMs else playerMs,
            speed = player.playbackParameters.speed
        )
    }

    private fun chapterChanged(player: Player): Boolean {
        val book = _state.value?.book ?: return false
        return currentChapterIndex(book.chapterStartsMs, player.currentPosition) != book.chapterIndex
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
