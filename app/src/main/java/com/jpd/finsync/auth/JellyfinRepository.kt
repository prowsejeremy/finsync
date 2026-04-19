package com.jpd.finsync.auth

import android.content.Context
import com.jpd.finsync.api.JellyfinClient
import com.jpd.finsync.model.AuthenticateRequest
import com.jpd.finsync.model.ItemsResponse
import com.jpd.finsync.model.MediaItem
import com.jpd.finsync.model.ServerConfig
import com.jpd.finsync.model.ServerInfo
import com.jpd.finsync.db.SyncDatabase
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
}

class JellyfinRepository(private val context: Context) {
    suspend fun isServerHealthy(serverUrl: String): Boolean {
        return try {
            val response = readApi(serverUrl).getHealth()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private val credentialStore = CredentialStore(context)
    private val activeAudioCall = AtomicReference<Call?>(null)

    fun cancelAudioDownload() {
        activeAudioCall.getAndSet(null)?.cancel()
    }

    private var _authApi: com.jpd.finsync.api.JellyfinApi? = null
    private var _readApi: com.jpd.finsync.api.JellyfinApi? = null
    private var _currentUrl: String? = null

    private fun authApi(serverUrl: String): com.jpd.finsync.api.JellyfinApi {
        if (_authApi == null || _currentUrl != serverUrl) rebuild(serverUrl)
        return _authApi!!
    }

    private fun readApi(serverUrl: String): com.jpd.finsync.api.JellyfinApi {
        if (_readApi == null || _currentUrl != serverUrl) rebuild(serverUrl)
        return _readApi!!
    }

    private fun rebuild(serverUrl: String) {
        _authApi = JellyfinClient.create(serverUrl, allowLoginPost = true,  debug = true)
        _readApi = JellyfinClient.create(serverUrl, allowLoginPost = false, debug = true)
        _currentUrl = serverUrl
    }

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
            JellyfinClient.buildAuthHeader(),
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

    suspend fun getAllAudioItems(config: ServerConfig): Result<List<MediaItem>> = safeCall {
        val items = mutableListOf<MediaItem>()
        val api   = readApi(config.serverUrl)
        var start = 0

        while (true) {
            val response = api.getAudioItems(
                userId     = config.userId,
                token      = config.accessToken,
                startIndex = start,
                limit      = 500
            )
            if (!response.isSuccessful) {
                return@safeCall Result.Error("Failed to fetch items: ${response.code()}")
            }
            val page = response.body()!!
            items.addAll(page.items)
            if (items.size >= page.totalRecordCount) break
            start += 500
        }
        Result.Success(items)
    }

    suspend fun getAlbums(config: ServerConfig): Result<ItemsResponse> = safeCall {
        val response = readApi(config.serverUrl).getAlbums(config.userId, config.accessToken)
        if (response.isSuccessful) Result.Success(response.body()!!)
        else Result.Error("Failed to fetch albums: ${response.code()}")
    }

    suspend fun getAlbumTracks(config: ServerConfig, albumId: String): Result<ItemsResponse> = safeCall {
        val response = readApi(config.serverUrl).getAlbumTracks(config.userId, config.accessToken, albumId)
        if (response.isSuccessful) Result.Success(response.body()!!)
        else Result.Error("Failed to fetch tracks: ${response.code()}")
    }

    suspend fun downloadAudio(config: ServerConfig, itemId: String): okhttp3.Response {
        val baseUrl = if (config.serverUrl.endsWith("/")) config.serverUrl else "${config.serverUrl}/"
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val url = "${baseUrl}Audio/${itemId}/stream?static=true&api_key=${config.accessToken}"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

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
        readApi(config.serverUrl).getAlbumArt(albumId, config.accessToken, maxWidth = maxWidth)

    private inline fun <T> safeCall(block: () -> Result<T>): Result<T> = try {
        block()
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error", e)
    }
}
