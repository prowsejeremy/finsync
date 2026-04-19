package com.jpd.finsync.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object JellyfinClient {

    private const val CONNECT_TIMEOUT_SEC  = 15L
    private const val READ_TIMEOUT_SEC     = 0L

    fun buildAuthHeader(
        clientName: String = "Finsync",
        deviceName: String = "Android",
        deviceId:   String = "finsync-android-001",
        version:    String = "1.0.0"
    ) = "MediaBrowser Client=\"$clientName\", Device=\"$deviceName\", " +
        "DeviceId=\"$deviceId\", Version=\"$version\""

    fun create(baseUrl: String, allowLoginPost: Boolean = false, debug: Boolean = false): JellyfinApi {
        val logging = HttpLoggingInterceptor().apply {
            level = if (debug) HttpLoggingInterceptor.Level.BASIC
                    else       HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC,        TimeUnit.SECONDS)
            .addInterceptor(ReadOnlyInterceptor(allowLoginPost))
            .addInterceptor(logging)
            .build()

        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JellyfinApi::class.java)
    }
}

class ReadOnlyInterceptor(private val allowLoginPost: Boolean) : Interceptor {

    companion object {
        private val READ_ONLY_METHODS = setOf("GET", "HEAD")
        private const val AUTH_PATH   = "Users/AuthenticateByName"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method  = request.method.uppercase()

        if (method in READ_ONLY_METHODS) {
            return chain.proceed(request)
        }

        if (allowLoginPost && method == "POST") {
            val path = request.url.encodedPath
            if (path.contains(AUTH_PATH, ignoreCase = true)) {
                return chain.proceed(request)
            }
        }

        throw ReadOnlyViolationException(
            "Blocked outgoing $method request to ${request.url} — " +
            "this app is read-only and must not modify the Jellyfin server."
        )
    }
}

class ReadOnlyViolationException(message: String) : SecurityException(message)
