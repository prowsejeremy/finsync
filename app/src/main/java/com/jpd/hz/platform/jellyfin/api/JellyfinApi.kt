package com.jpd.hz.platform.jellyfin.api

import com.jpd.hz.platform.jellyfin.api.AuthenticateRequest
import com.jpd.hz.platform.jellyfin.api.AuthenticateResponse
import com.jpd.hz.platform.jellyfin.api.ItemsResponse
import com.jpd.hz.platform.jellyfin.api.QuickConnectRequest
import com.jpd.hz.platform.jellyfin.api.QuickConnectResult
import com.jpd.hz.platform.jellyfin.api.ServerInfo
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface JellyfinApi {
    /** The signed-in user; only its status code is read (spec "Sign-in health", decision 3). */
    @GET("Users/Me")
    suspend fun getMe(@Header("Authorization") authorization: String): Response<ResponseBody>

    @POST("Users/AuthenticateByName")
    @Headers("Content-Type: application/json")
    suspend fun authenticateByName(
        @Header("Authorization") authorization: String,
        @Body body: AuthenticateRequest
    ): Response<AuthenticateResponse>

    /**
     * A new Quick Connect code (spec "Quick Connect sign-in", decision 3); 401 when Quick Connect
     * is off on the server. Sent with no body.
     */
    @POST("QuickConnect/Initiate")
    suspend fun initiateQuickConnect(
        @Header("Authorization") authorization: String
    ): Response<QuickConnectResult>

    /** The code's state; 404 once the server has forgotten it. */
    @GET("QuickConnect/Connect")
    suspend fun getQuickConnectState(
        @Header("Authorization") authorization: String,
        @Query("secret") secret: String
    ): Response<QuickConnectResult>

    @POST("Users/AuthenticateWithQuickConnect")
    @Headers("Content-Type: application/json")
    suspend fun authenticateWithQuickConnect(
        @Header("Authorization") authorization: String,
        @Body body: QuickConnectRequest
    ): Response<AuthenticateResponse>

    @GET("System/Info/Public")
    suspend fun getPublicServerInfo(): Response<ServerInfo>

    @GET("Users/{userId}/Items")
    suspend fun getAudioItems(
        @Path("userId") userId: String,
        @Header("Authorization") authorization: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "Audio",
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "Path,MediaSources,Genres,Artists,ArtistItems,AlbumArtist,UserData,DateCreated,PremiereDate,DateModified",
        @Query("SortBy") sortBy: String = "AlbumArtist,Album,SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 500
    ): Response<ItemsResponse>

    @GET("Users/{userId}/Items/{itemId}/Download")
    @Streaming
    suspend fun downloadAudio(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>

    @GET("Items/{itemId}/Images/Primary")
    @Streaming
    suspend fun getAlbumArt(
        @Path("itemId") itemId: String,
        @Header("Authorization") authorization: String,
        @Query("quality") quality: Int = 90,
        @Query("maxWidth") maxWidth: Int = 600
    ): Response<ResponseBody>

    // 3b's requests use routes Jellyfin 12.1.0's OpenAPI documents. The Users/{userId}/Items
    // calls above aren't in it but still work, so they stay. A null fields is left out.
    @GET("Items")
    suspend fun getItems(
        @Header("Authorization") authorization: String,
        @Query("userId") userId: String,
        @Query("includeItemTypes") includeItemTypes: String,
        @Query("recursive") recursive: Boolean = true,
        @Query("fields") fields: String? = null,
        @Query("sortBy") sortBy: String = "SortName",
        @Query("sortOrder") sortOrder: String = "Ascending",
        @Query("startIndex") startIndex: Int = 0,
        @Query("limit") limit: Int = 500
    ): Response<ItemsResponse>

    /** A playlist's entries in server order, each under the track's own item ID. */
    @GET("Playlists/{playlistId}/Items")
    suspend fun getPlaylistItems(
        @Path("playlistId") playlistId: String,
        @Header("Authorization") authorization: String,
        @Query("userId") userId: String,
        @Query("startIndex") startIndex: Int = 0,
        @Query("limit") limit: Int = 500
    ): Response<ItemsResponse>
}
