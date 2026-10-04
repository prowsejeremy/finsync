package com.jpd.finsync.sync

import android.content.Context
import android.util.Log
import com.jpd.finsync.auth.JellyfinRepository
import com.jpd.finsync.library.ArtistPhotos
import com.jpd.finsync.library.LibraryRepository
import com.jpd.finsync.library.stalePhotoFileNames
import com.jpd.finsync.model.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException

private const val TAG = "ArtistPhotoSync"
private const val HTTP_NOT_FOUND = 404
private const val PARTIAL_SUFFIX = ".part"

/**
 * The end of a sync: fetches the missing photo of each album artist of a downloaded album, then
 * deletes photos no downloaded album needs. Failures are logged and never fail the sync.
 */
object ArtistPhotoSync {

    suspend fun run(context: Context, config: ServerConfig, jellyfin: JellyfinRepository) {
        try {
            val artistIds = LibraryRepository(context).downloadedAlbumArtistIds()
            val folder = ArtistPhotos.folder(context)
            withContext(Dispatchers.IO) { folder.mkdirs() }
            for (artistId in artistIds) {
                val photo = ArtistPhotos.file(context, artistId)
                // Fetched only when missing; the next sync retries any that failed.
                if (!photo.isFile) fetchPhoto(config, jellyfin, artistId, photo)
            }
            deleteStalePhotos(folder, artistIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Artist photos skipped", e)
        }
    }

    private suspend fun fetchPhoto(
        config: ServerConfig,
        jellyfin: JellyfinRepository,
        artistId: String,
        photo: File
    ) {
        try {
            // The album art endpoint serves any item's primary image, artists included.
            val response = jellyfin.downloadAlbumArt(config, artistId)
            val body = response.body()
            when {
                response.isSuccessful && body != null -> savePhoto(body, photo)
                // No photo on the server: write nothing.
                response.code() == HTTP_NOT_FOUND -> response.errorBody()?.close()
                else -> {
                    response.errorBody()?.close()
                    Log.w(TAG, "Photo for artist $artistId: HTTP ${response.code()}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't fetch the photo for artist $artistId", e)
        }
    }

    // Written beside the photo and renamed last, so a half-written photo never shows.
    private suspend fun savePhoto(body: ResponseBody, photo: File) = withContext(Dispatchers.IO) {
        val partial = File(photo.path + PARTIAL_SUFFIX)
        val written = body.use { source ->
            partial.outputStream().use { out -> source.byteStream().copyTo(out) }
        }
        if (written == 0L) {
            partial.delete()
            return@withContext
        }
        if (!partial.renameTo(photo)) {
            partial.delete()
            throw IOException("Couldn't save ${photo.name}")
        }
    }

    private suspend fun deleteStalePhotos(folder: File, keepArtistIds: Set<String>) =
        withContext(Dispatchers.IO) {
            val names = folder.list()?.toList() ?: return@withContext
            for (name in stalePhotoFileNames(names, keepArtistIds)) {
                if (!File(folder, name).delete()) Log.w(TAG, "Couldn't delete artist photo $name")
            }
        }
}
