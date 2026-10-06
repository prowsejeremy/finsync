package com.jpd.hz.sync

import android.util.Log
import com.jpd.hz.auth.JellyfinRepository
import com.jpd.hz.model.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException

private const val TAG = "PrimaryImage"
private const val HTTP_NOT_FOUND = 404
private const val PARTIAL_SUFFIX = ".part"

/**
 * Saves an item's primary image (a playlist or book cover) to [target]. When the server has none
 * (404) nothing is written; any other failure is logged and never thrown, except cancellation.
 */
internal suspend fun fetchPrimaryImage(
    jellyfin: JellyfinRepository,
    config: ServerConfig,
    itemId: String,
    target: File
) {
    try {
        // The album art endpoint serves any item's primary image.
        val response = jellyfin.downloadAlbumArt(config, itemId)
        val body = response.body()
        when {
            response.isSuccessful && body != null -> saveImage(body, target)
            response.code() == HTTP_NOT_FOUND -> response.errorBody()?.close()
            else -> {
                response.errorBody()?.close()
                Log.w(TAG, "Image for $itemId: HTTP ${response.code()}")
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't fetch the image for $itemId", e)
    }
}

// Written beside the target and renamed last, so a half-written image never shows.
private suspend fun saveImage(body: ResponseBody, target: File) = withContext(Dispatchers.IO) {
    target.parentFile?.mkdirs()
    val partial = File(target.path + PARTIAL_SUFFIX)
    val written = body.use { source ->
        partial.outputStream().use { out -> source.byteStream().copyTo(out) }
    }
    if (written == 0L) {
        partial.delete()
        return@withContext
    }
    if (!partial.renameTo(target)) {
        partial.delete()
        throw IOException("Couldn't save ${target.name}")
    }
}
