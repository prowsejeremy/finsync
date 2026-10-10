package com.jpd.hz.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.jpd.hz.equaliser.EqualiserStore
import com.jpd.hz.library.BookRepository
import com.jpd.hz.library.LibraryRepository
import com.jpd.hz.library.db.BookProgress
import com.jpd.hz.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PlaybackService"
// "Process death while playing: the saved position is at most 10 s old" (spec). A book's
// progress saves on the same tick (3b "Progress").
private const val SAVE_INTERVAL_MS = 10_000L

/**
 * A saved queue, resolved to playable items. [index] is the playing track among [items]; [order]
 * is ResumeState's queue.
 */
private class RestoredQueue(
    val items: List<MediaItem>,
    val order: List<Int>,
    val index: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffle: Boolean
)

/**
 * Background playback. Media3 supplies the notification (its default, on its own channel), lock
 * screen and Bluetooth controls; BassPlayer plays. While a book is current, its place is saved
 * to book_progress alongside the resume state (3b).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: BassPlayer
    private lateinit var focus: PlaybackFocus
    private lateinit var resolver: TrackResolver
    private lateinit var resumeStore: ResumeStore
    private lateinit var progressWriter: BookProgressWriter
    private lateinit var equaliserStore: EqualiserStore
    private lateinit var restored: Deferred<RestoredQueue?>
    private var session: MediaSession? = null
    private var saveTicker: Job? = null
    // No saving until the restore decision is made, or the empty startup queue would wipe it.
    private var restoreSettled = false
    private var resumptionRequested = false
    // A book restored paused isn't saved until it plays or seeks, so Finished survives a restart.
    private var untouchedBookId: String? = null

    private val saveListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (savesResume(events::contains, player.playbackState)) saveResume()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) untouchedBookId = null
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) untouchedBookId = null
            saveTicker?.cancel()
            saveTicker = if (isPlaying) {
                scope.launch {
                    while (isActive) {
                        delay(SAVE_INTERVAL_MS)
                        saveResume()
                    }
                }
            } else {
                null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val engine = BassEngine(applicationInfo.nativeLibraryDir)
        // Before anything can start BASS, so the first track already has the EQ (5). Changes
        // saved by the Equaliser screen reach the engine through the listener.
        equaliserStore = EqualiserStore(this)
        engine.setEqualiser(equaliserStore.load().activeGainsDb())
        equaliserStore.listen { settings -> engine.setEqualiser(settings.activeGainsDb()) }
        val speedStore = BookSpeedStore(this)
        player = BassPlayer(Looper.getMainLooper(), engine)
        player.bookSpeed = speedStore.load()
        player.onBookSpeedChanged = speedStore::save
        focus = PlaybackFocus(this, player, engine)
        val books = BookRepository(this)
        resolver = TrackResolver(LibraryRepository(this), books)
        progressWriter = BookProgressWriter(books)
        resumeStore = ResumeStore(this)
        restored = scope.async { loadRestorableQueue() }
        player.addListener(saveListener)
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(openAppIntent())
            .build()
        scope.launch {
            try {
                val queue = restored.await()
                // "When the service starts with an empty queue, it restores them paused" (spec).
                if (!resumptionRequested && queue != null && player.mediaItemCount == 0) {
                    applyQueueSettings(queue)
                    player.setMediaItems(queue.items, queue.index, queue.positionMs)
                    player.prepare()
                    untouchedBookId = player.bookPosition()?.bookId
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't restore queue", e)
            } finally {
                restoreSettled = true
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away: keep playing if playing, otherwise there's no reason to stay alive.
        val ended = player.playbackState == Player.STATE_ENDED
        if (!player.playWhenReady || player.mediaItemCount == 0 || ended) stopSelf()
    }

    override fun onDestroy() {
        // First, so no change reaches the engine while it's being released.
        equaliserStore.stopListening()
        saveResume()
        // After the last save: the writer drains its queue on its own scope (3b).
        progressWriter.close()
        saveTicker?.cancel()
        scope.cancel()
        focus.release()
        player.removeListener(saveListener)
        player.release()
        session?.release()
        session = null
        super.onDestroy()
    }

    // The queue, and the current book's place when a book is current (3b "Progress").
    private fun saveResume() {
        if (!restoreSettled) return
        resumeStore.save(player.resumeState())
        player.bookPosition()?.takeIf { it.bookId != untouchedBookId }?.let { book ->
            progressWriter.save(
                BookProgress(
                    bookId = book.bookId,
                    positionMs = book.positionMs,
                    finished = book.ended,
                    lastPlayedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun loadRestorableQueue(): RestoredQueue? {
        val saved = resumeStore.load() ?: return null
        val resolved = resolver.resolve(saved.sourceIds)
        // Judged per position, as items is filtered below, so the two always line up.
        val state = saved.keepOnly(resolved.map { it != null }) ?: return null
        return RestoredQueue(
            items = resolved.filterNotNull(),
            order = state.queue,
            index = state.queue[state.index],
            positionMs = state.positionMs,
            repeatMode = state.repeatMode,
            shuffle = state.shuffle
        )
    }

    // Both restore paths call this just before the player gets the items: the service's own
    // setMediaItems, or Media3's, which runs on the main thread as soon as onPlaybackResumption
    // hands them back. So the saved order is never left waiting for a later, unrelated play.
    private fun applyQueueSettings(queue: RestoredQueue) {
        player.repeatMode = queue.repeatMode
        player.shuffleModeEnabled = queue.shuffle
        player.restoreQueueOrder(queue.items.map { it.mediaId }, queue.order)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private inner class SessionCallback : MediaSession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()
            scope.launch {
                try {
                    val resolved = resolver.resolve(mediaItems.map { it.mediaId })
                    future.set(resolved.filterNotNull().toMutableList())
                } catch (e: CancellationException) {
                    future.cancel(false)
                    throw e
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // The queue is about to be replaced: save the outgoing book's place first (3b).
            saveResume()
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                try {
                    val resolved = resolver.resolve(mediaItems.map { it.mediaId })
                    val start = remapStartIndex(resolved.map { it != null }, startIndex)
                    if (start == null) {
                        // Nothing playable: failing leaves the queue as it was (spec).
                        future.setException(IllegalStateException("No requested track plays"))
                    } else {
                        val startKept = resolved.getOrNull(startIndex.coerceAtLeast(0)) != null
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                resolved.filterNotNull(),
                                start,
                                if (startKept) startPositionMs else 0L
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    future.cancel(false)
                    throw e
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }

        // Bluetooth or lock-screen Play with nothing queued: offer the saved queue.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            resumptionRequested = true
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                try {
                    val queue = restored.await()
                    restoreSettled = true
                    if (queue == null) {
                        future.setException(IllegalStateException("Nothing to resume"))
                    } else {
                        applyQueueSettings(queue)
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                queue.items, queue.index, queue.positionMs
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    future.cancel(false)
                    throw e
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
    }
}
