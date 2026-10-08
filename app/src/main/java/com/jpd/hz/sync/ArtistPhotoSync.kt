package com.jpd.hz.sync

import android.util.Log
import com.jpd.hz.auth.JellyfinRepository
import com.jpd.hz.model.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ArtistPhotoSync"

/**
 * After cleanup: fetches each album artist's missing photo straight into the sync folder, as
 * Music/<album artist>/artist.jpg (spec "Other files it writes"), where the scanner finds it.
 * filesToKeep lists every one, so cleanup removes a photo no album needs any more. Failures are
 * logged and never fail the sync; the next sync tries again.
 */
internal object ArtistPhotoSync {

    suspend fun run(
        config: ServerConfig,
        jellyfin: JellyfinRepository,
        syncDir: File,
        plan: SyncPlan
    ) {
        try {
            for ((path, artistId) in artistPhotosOf(plan.tracks)) {
                val photo = File(syncDir, path)
                if (isMissingBesideMusic(photo)) {
                    fetchPrimaryImage(jellyfin, config, artistId, photo)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Artist photos skipped", e)
        }
    }

    // Only beside music on the device, and only once: a photo is fetched when it's missing.
    private suspend fun isMissingBesideMusic(photo: File): Boolean = withContext(Dispatchers.IO) {
        photo.parentFile?.isDirectory == true && !photo.isFile
    }
}
