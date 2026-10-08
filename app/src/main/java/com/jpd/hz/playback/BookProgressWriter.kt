package com.jpd.hz.playback

import android.util.Log
import com.jpd.hz.library.BookRepository
import com.jpd.hz.library.db.BookProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private const val TAG = "BookProgressWriter"

/**
 * Writes book positions to the library's book_progress off the main thread, in the order they were
 * saved, so an older position never lands after a newer one. Its scope isn't the service's: the
 * save made
 * in onDestroy still lands after the service's scope is cancelled (decision 16).
 */
class BookProgressWriter(private val books: BookRepository) {

    private val saves = Channel<BookProgress>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            for (progress in saves) write(progress)
        }
    }

    fun save(progress: BookProgress) {
        // Fails only after close(), when the service is already going.
        if (saves.trySend(progress).isFailure) {
            Log.w(TAG, "Progress for ${progress.bookId} arrived after close")
        }
    }

    /** Writes whatever is queued, then stops. */
    fun close() {
        saves.close()
    }

    private suspend fun write(progress: BookProgress) {
        try {
            books.saveBookProgress(progress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't save progress for ${progress.bookId}", e)
        }
    }
}
