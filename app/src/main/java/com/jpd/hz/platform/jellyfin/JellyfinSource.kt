package com.jpd.hz.platform.jellyfin

import android.content.Context
import android.util.Log
import com.jpd.hz.R
import com.jpd.hz.adapter.Availability
import com.jpd.hz.adapter.CatalogueResult
import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ExtraFile
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.NO_ALBUM_GROUP
import com.jpd.hz.adapter.Source
import com.jpd.hz.adapter.SourceItem
import com.jpd.hz.adapter.run.SyncPlan
import com.jpd.hz.model.ServerConfig
import com.jpd.hz.platform.jellyfin.api.MediaItem
import com.jpd.hz.platform.jellyfin.api.ServerCheck
import com.jpd.hz.platform.jellyfin.api.SignInStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection.HTTP_NOT_FOUND

private const val TAG = "JellyfinSource"

/**
 * One Jellyfin sign-in as the harness reads it (spec "Fits the harness"): the catalogue from
 * `Items`, files from `Audio/{id}/stream?static=true`, covers and artist photos from each item's
 * primary image, and our tags from [JellyfinTagMapping].
 */
class JellyfinSource(
    context: Context,
    private val config: ServerConfig,
    private val repo: JellyfinRepository = JellyfinRepository(context)
) : Source {

    private val noAlbumName = context.getString(R.string.choice_no_album)

    // The last fetch's items by ID, for the artist photos the plan needs.
    @Volatile
    private var fetched: Map<String, MediaItem> = emptyMap()

    override val signInRefused: Flow<Boolean> =
        SignInStatus.shared.refusedToken.map { SignInStatus.isRefused(it, config.accessToken) }

    override suspend fun checkAvailability(): Availability = when (repo.checkServer(config)) {
        ServerCheck.CONNECTED -> Availability.AVAILABLE
        ServerCheck.REFUSED -> Availability.SIGN_IN_REFUSED
        ServerCheck.OFFLINE -> Availability.UNREACHABLE
    }

    override suspend fun catalogue(): CatalogueResult =
        when (val result = repo.getServerCatalogue(config)) {
            is Result.Error -> CatalogueResult.Failure(result.message)
            is Result.Success -> {
                fetched = (result.data.audio + result.data.books).associateBy { it.id }
                CatalogueResult.Success(
                    JellyfinCatalogueMapping.sourceCatalogueOf(result.data, noAlbumName)
                )
            }
        }

    override suspend fun open(item: SourceItem): InputStream {
        // The download call blocks until the response's headers arrive.
        val response = withContext(Dispatchers.IO) { repo.downloadAudio(config, item.id) }
        val body = response.body
        if (!response.isSuccessful || body == null) {
            response.close()
            throw IOException("Server returned ${response.code} for ${item.id}")
        }
        return body.byteStream()
    }

    // Albums, playlists and books each have a primary image; songs with no album share none.
    override suspend fun openGroupImage(group: ChoiceGroup): InputStream? =
        if (group.id == NO_ALBUM_GROUP) null else primaryImage(group.id)

    override fun extras(plan: SyncPlan): List<ExtraFile> {
        val tracks = plan.items.filter { it.kind == ItemKind.MUSIC }.mapNotNull { fetched[it.id] }
        return JellyfinLayout.artistPhotosOf(tracks).map { (path, artistId) ->
            ExtraFile(path) { primaryImage(artistId) }
        }
    }

    // The album art endpoint serves any item's primary image. None (404) is null; any other
    // failure throws, and the harness logs it and tries again next sync.
    private suspend fun primaryImage(itemId: String): InputStream? {
        val response = repo.downloadAlbumArt(config, itemId)
        val body = response.body()
        if (response.isSuccessful && body != null) return body.byteStream()
        response.errorBody()?.close()
        if (response.code() == HTTP_NOT_FOUND) return null
        Log.w(TAG, "Image for $itemId: HTTP ${response.code()}")
        throw IOException("Image for $itemId: HTTP ${response.code()}")
    }
}
