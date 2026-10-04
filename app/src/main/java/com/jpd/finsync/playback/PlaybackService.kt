package com.jpd.finsync.playback

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
import com.jpd.finsync.library.LibraryRepository
import com.jpd.finsync.ui.MainActivity
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
// "Process death while playing: the saved position is at most 10 s old" (spec).
private const val SAVE_INTERVAL_MS = 10_000L
private val SAVE_EVENTS = intArrayOf(
    Player.EVENT_PLAY_WHEN_READY_CHANGED,
    Player.EVENT_MEDIA_ITEM_TRANSITION,
    Player.EVENT_POSITION_DISCONTINUITY,
    Player.EVENT_TIMELINE_CHANGED,
    Player.EVENT_REPEAT_MODE_CHANGED,
    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
)

/** A saved queue, already resolved to playable items. */
private class RestoredQueue(
    val items: List<MediaItem>,
    val index: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffle: Boolean
)

/**
 * Background playback. Media3 supplies the notification (its default, on its own channel), lock
 * screen and Bluetooth controls; BassPlayer plays.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: BassPlayer
    private lateinit var focus: PlaybackFocus
    private lateinit var resolver: TrackResolver
    private lateinit var resumeStore: ResumeStore
    private lateinit var restored: Deferred<RestoredQueue?>
    private var session: MediaSession? = null
    private var saveTicker: Job? = null
    // No saving until the restore decision is made, or the empty startup queue would wipe it.
    private var restoreSettled = false
    private var resumptionRequested = false

    private val saveListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(*SAVE_EVENTS)) saveResume()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
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
        player = BassPlayer(Looper.getMainLooper(), engine)
        focus = PlaybackFocus(this, player, engine)
        resolver = TrackResolver(LibraryRepository(this))
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
        saveResume()
        saveTicker?.cancel()
        scope.cancel()
        focus.release()
        player.removeListener(saveListener)
        player.release()
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun saveResume() {
        if (restoreSettled) resumeStore.save(player.resumeState())
    }

    private suspend fun loadRestorableQueue(): RestoredQueue? {
        val saved = resumeStore.load() ?: return null
        val resolved = resolver.resolve(saved.itemIds)
        val available = saved.itemIds.filterIndexed { index, _ -> resolved[index] != null }.toSet()
        val state = saved.keepOnly(available) ?: return null
        return RestoredQueue(
            items = resolved.filterNotNull(),
            index = state.index,
            positionMs = state.positionMs,
            repeatMode = state.repeatMode,
            shuffle = state.shuffle
        )
    }

    private fun applyQueueSettings(queue: RestoredQueue) {
        player.repeatMode = queue.repeatMode
        player.shuffleModeEnabled = queue.shuffle
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
