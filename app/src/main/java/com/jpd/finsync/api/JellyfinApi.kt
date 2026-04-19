package com.jpd.finsync.api

import com.jpd.finsync.model.AuthenticateRequest
import com.jpd.finsync.model.AuthenticateResponse
import com.jpd.finsync.model.ItemsResponse
import com.jpd.finsync.model.ServerInfo
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface JellyfinApi {
    @GET("health")
    suspend fun getHealth(): Response<ResponseBody>

    @POST("Users/AuthenticateByName")
    @Headers("Content-Type: application/json")
    suspend fun authenticateByName(
        @Header("X-Emby-Authorization") authHeader: String,
        @Body body: AuthenticateRequest
    ): Response<AuthenticateResponse>

    @GET("System/Info/Public")
    suspend fun getPublicServerInfo(): Response<ServerInfo>

    @GET("Users/{userId}/Items")
    suspend fun getAudioItems(
        @Path("userId") userId: String,
        @Header("X-MediaBrowser-Token") token: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "Audio",
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "Path,MediaSources,Genres,Artists,ArtistItems,AlbumArtist,UserData,DateCreated,PremiereDate,DateModified",
        @Query("SortBy") sortBy: String = "AlbumArtist,Album,SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 500
    ): Response<ItemsResponse>

    @GET("Users/{userId}/Items")
    suspend fun getAlbums(
        @Path("userId") userId: String,
        @Header("X-MediaBrowser-Token") token: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "MusicAlbum",
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "AlbumArtist,ChildCount,DateCreated,PremiereDate",
        @Query("SortBy") sortBy: String = "AlbumArtist,SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 500
    ): Response<ItemsResponse>

    @GET("Users/{userId}/Items")
    suspend fun getAlbumTracks(
        @Path("userId") userId: String,
        @Header("X-MediaBrowser-Token") token: String,
        @Query("ParentId") albumId: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "Audio",
        @Query("Fields") fields: String = "Path,MediaSources,Artists,UserData",
        @Query("SortBy") sortBy: String = "ParentIndexNumber,IndexNumber,SortName",
        @Query("SortOrder") sortOrder: String = "Ascending"
    ): Response<ItemsResponse>

    @GET("Users/{userId}/Items/{itemId}/Download")
    @Streaming
    suspend fun downloadAudio(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Header("X-MediaBrowser-Token") token: String
    ): Response<ResponseBody>

    @GET("Items/{itemId}/Images/Primary")
    @Streaming
    suspend fun getAlbumArt(
        @Path("itemId") itemId: String,
        @Header("X-MediaBrowser-Token") token: String,
        @Query("quality") quality: Int = 90,
        @Query("maxWidth") maxWidth: Int = 600
    ): Response<ResponseBody>
}
