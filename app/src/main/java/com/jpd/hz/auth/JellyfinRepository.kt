package com.jpd.hz.auth

import android.content.Context
import com.jpd.hz.api.DeviceIdentity
import com.jpd.hz.api.JellyfinClient
import com.jpd.hz.api.RefusedSignInInterceptor
import com.jpd.hz.api.ServerCheck
import com.jpd.hz.model.AuthenticateRequest
import com.jpd.hz.model.ItemsResponse
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.ServerCatalogue
import com.jpd.hz.model.ServerConfig
import com.jpd.hz.model.ServerInfo
import com.jpd.hz.model.ServerPlaylist
import com.jpd.hz.db.SyncDatabase
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import android.util.Log
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "JellyfinRepository"
private const val PAGE_SIZE = 500
private const val PLAYLIST_TYPE = "Playlist"
private const val AUDIOBOOK_TYPE = "AudioBook"
// Only values ItemFields lists (P0): the file, its audio details, chapters and authors, plus
// the genres sync writes into the file's tags (T2).
private const val BOOK_FIELDS = "Path,MediaSources,Chapters,People,Genres"

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
}

class JellyfinRepository(private val context: Context) {
    /**
     * Whether the server answers and takes [config]'s sign-in (spec "Sign-in health", decision
     * 3). A 401 is also recorded by RefusedSignInInterceptor, which the screens read.
     */
    suspend fun checkServer(config: ServerConfig): ServerCheck = try {
        val response = readApi(config.serverUrl).getMe(authorization(config))
        // Only the code matters; the body is closed either way.
        response.body()?.close()
        response.errorBody()?.close()
        ServerCheck.from(response.code())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // No response: offline, unreachable, or a saved URL that no longer parses.
        Log.w(TAG, "Server check failed: ${e.message}")
        ServerCheck.OFFLINE
    }

    private val credentialStore = CredentialStore(context)
    private val activeAudioCall = AtomicReference<Call?>(null)

    // The install's (DeviceIdentity): one file read, the first time a request needs it.
    private val deviceId by lazy { DeviceIdentity.of(context) }

    fun cancelAudioDownload() {
        activeAudioCall.getAndSet(null)?.cancel()
    }

    private var _authApi: com.jpd.hz.api.JellyfinApi? = null
    private var _readApi: com.jpd.hz.api.JellyfinApi? = null
    private var _currentUrl: String? = null

    private fun authApi(serverUrl: String): com.jpd.hz.api.JellyfinApi {
        if (_authApi == null || _currentUrl != serverUrl) rebuild(serverUrl)
        return _authApi!!
    }

    private fun readApi(serverUrl: String): com.jpd.hz.api.JellyfinApi {
        if (_readApi == null || _currentUrl != serverUrl) rebuild(serverUrl)
        return _readApi!!
    }

    private fun rebuild(serverUrl: String) {
        _authApi = JellyfinClient.create(serverUrl, allowLoginPost = true,  debug = true)
        _readApi = JellyfinClient.create(serverUrl, allowLoginPost = false, debug = true)
        _currentUrl = serverUrl
    }

    private fun authorization(config: ServerConfig) =
        JellyfinClient.buildAuthHeader(deviceId, token = config.accessToken)

    suspend fun testServer(serverUrl: String): Result<ServerInfo> = safeCall {
        val response = readApi(serverUrl).getPublicServerInfo()
        if (response.isSuccessful) Result.Success(response.body()!!)
        else Result.Error("Server returned ${response.code()}: ${response.message()}")
    }

    suspend fun login(
        serverUrl: String,
        username: String,
        password: String
    ): Result<ServerConfig> = safeCall {
        val response = authApi(serverUrl).authenticateByName(
            JellyfinClient.buildAuthHeader(deviceId),
            AuthenticateRequest(username, password)
        )
        if (!response.isSuccessful) {
            return@safeCall Result.Error("Login failed (${response.code()}): ${response.message()}")
        }
        val body = response.body()!!

        val serverInfoResp = readApi(serverUrl).getPublicServerInfo()
        val serverName = if (serverInfoResp.isSuccessful)
            serverInfoResp.body()?.serverName ?: "Jellyfin"
        else "Jellyfin"

        val config = ServerConfig(
            serverUrl   = serverUrl,
            userId      = body.user.id,
            accessToken = body.accessToken,
            username    = body.user.name,
            serverId    = body.serverId,
            serverName  = serverName
        )
        credentialStore.save(config)
        Result.Success(config)
    }

    // Logout will clear saved credentials, but it won't delete any of the synced files or DB entries.
    suspend fun logout(context: Context) {
        credentialStore.clear()
    }

    fun getSavedConfig()      = credentialStore.load()
    fun isLoggedIn()          = credentialStore.isLoggedIn()

    /**
     * Everything sync and the catalogue refresh read (3b): the audio items, the user's playlists
     * with each one's entries, then the audiobooks. The audio list failing fails the fetch, as
     * before 3b. A failed playlist or book part is recorded on the result and the rest carries
     * on (decision 3).
     */
    suspend fun getServerCatalogue(config: ServerConfig): Result<ServerCatalogue> = safeCall {
        val api = readApi(config.serverUrl)
        val auth = authorization(config)
        val audio = fetchAllPages("audio items") { start ->
            api.getAudioItems(
                userId = config.userId,
                authorization = auth,
                startIndex = start,
                limit = PAGE_SIZE
            )
        }
        val playlistList = partOrNull("playlists") {
            fetchAllPages("playlists") { start ->
                api.getItems(
                    authorization = auth,
                    userId = config.userId,
                    includeItemTypes = PLAYLIST_TYPE,
                    startIndex = start,
                    limit = PAGE_SIZE
                )
            }
        }
        val playlists = mutableListOf<ServerPlaylist>()
        val failedPlaylistIds = mutableSetOf<String>()
        for (playlist in playlistList.orEmpty()) {
            val entries = partOrNull("playlist ${playlist.id}") {
                fetchAllPages("playlist ${playlist.id}") { start ->
                    api.getPlaylistItems(
                        playlistId = playlist.id,
                        authorization = auth,
                        userId = config.userId,
                        startIndex = start,
                        limit = PAGE_SIZE
                    )
                }
            }
            if (entries == null) {
                failedPlaylistIds.add(playlist.id)
            } else {
                playlists.add(ServerPlaylist(playlist, entries))
            }
        }
        val books = partOrNull("audiobooks") {
            fetchAllPages("audiobooks") { start ->
                api.getItems(
                    authorization = auth,
                    userId = config.userId,
                    includeItemTypes = AUDIOBOOK_TYPE,
                    fields = BOOK_FIELDS,
                    startIndex = start,
                    limit = PAGE_SIZE
                )
            }
        }
        Result.Success(
            ServerCatalogue(
                audio = audio,
                playlists = playlists,
                books = books.orEmpty(),
                playlistsFailed = playlistList == null,
                failedPlaylistIds = failedPlaylistIds,
                booksFailed = books == null
            )
        )
    }

    // A failed playlist or book part is logged and reported as null, not thrown (decision 3).
    private suspend fun <T> partOrNull(what: String, fetch: suspend () -> T): T? = try {
        fetch()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't fetch $what; it keeps what it had and retries next sync", e)
        null
    }

    // Pages through a list endpoint. An HTTP error throws, and safeCall turns it into Result.Error.
    private suspend fun fetchAllPages(
        what: String,
        fetchPage: suspend (startIndex: Int) -> retrofit2.Response<ItemsResponse>
    ): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        while (true) {
            val response = fetchPage(items.size)
            val page = response.body()
            if (!response.isSuccessful || page == null) {
                throw IOException("Failed to fetch $what: ${response.code()}")
            }
            items.addAll(page.items)
            // An empty page ends it too, so a wrong total can't loop forever.
            if (page.items.isEmpty() || items.size >= page.totalRecordCount) return items
        }
    }

    suspend fun getAlbums(config: ServerConfig): Result<ItemsResponse> = safeCall {
        val response = readApi(config.serverUrl).getAlbums(config.userId, authorization(config))
        if (response.isSuccessful) Result.Success(response.body()!!)
        else Result.Error("Failed to fetch albums: ${response.code()}")
    }

    suspend fun getAlbumTracks(config: ServerConfig, albumId: String): Result<ItemsResponse> = safeCall {
        val response = readApi(config.serverUrl).getAlbumTracks(config.userId, authorization(config), albumId)
        if (response.isSuccessful) Result.Success(response.body()!!)
        else Result.Error("Failed to fetch tracks: ${response.code()}")
    }

    suspend fun downloadAudio(config: ServerConfig, itemId: String): okhttp3.Response {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(RefusedSignInInterceptor())
            .build()

        val request = JellyfinClient.buildAudioStreamRequest(
            baseUrl  = config.serverUrl,
            itemId   = itemId,
            token    = config.accessToken,
            deviceId = deviceId
        )

        val call = client.newCall(request)
        activeAudioCall.set(call)
        return try {
            call.execute()
        } finally {
            activeAudioCall.compareAndSet(call, null)
        }
    }

    suspend fun downloadAlbumArt(
        config: ServerConfig,
        albumId: String,
        maxWidth: Int = 600
    ): retrofit2.Response<ResponseBody> =
        readApi(config.serverUrl).getAlbumArt(albumId, authorization(config), maxWidth = maxWidth)

    private inline fun <T> safeCall(block: () -> Result<T>): Result<T> = try {
        block()
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error", e)
    }
}
