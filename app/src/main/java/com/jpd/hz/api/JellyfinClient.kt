package com.jpd.hz.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object JellyfinClient {

    private const val CONNECT_TIMEOUT_SEC  = 15L
    private const val READ_TIMEOUT_SEC     = 0L

    // The token in a header buildAuthHeader made: Token="…".
    private val TOKEN = Regex("Token=\"([^\"]+)\"")

    /**
     * Value for the standard `Authorization` header using Jellyfin's `MediaBrowser` scheme.
     *
     * Jellyfin 12 disabled the Emby-era `X-Emby-Authorization`, `X-MediaBrowser-Token` and
     * `api_key` mechanisms by default, so this header is the only supported way to identify
     * the client and, once logged in, to carry the access token. [deviceId] is the install's
     * (DeviceIdentity): Jellyfin ends a device's other sessions when it signs in again.
     */
    fun buildAuthHeader(
        deviceId:   String,
        clientName: String = "hz",
        deviceName: String = "Android",
        version:    String = "1.0.0",
        token:      String? = null
    ): String {
        val identity = "MediaBrowser Client=\"$clientName\", Device=\"$deviceName\", " +
            "DeviceId=\"$deviceId\", Version=\"$version\""
        return if (token.isNullOrBlank()) identity else "$identity, Token=\"$token\""
    }

    /** The token in a header [buildAuthHeader] made, or null when it carries none. */
    fun tokenOf(authorization: String): String? = TOKEN.find(authorization)?.groupValues?.get(1)

    /** Raw audio download request, authenticated via header so the token never appears in a URL. */
    fun buildAudioStreamRequest(
        baseUrl: String,
        itemId: String,
        token: String,
        deviceId: String
    ): Request =
        Request.Builder()
            .url("${normalizeBaseUrl(baseUrl)}Audio/$itemId/stream?static=true")
            .header("Authorization", buildAuthHeader(deviceId, token = token))
            .get()
            .build()

    fun create(baseUrl: String, allowLoginPost: Boolean = false, debug: Boolean = false): JellyfinApi {
        val logging = HttpLoggingInterceptor().apply {
            level = if (debug) HttpLoggingInterceptor.Level.BASIC
                    else       HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC,        TimeUnit.SECONDS)
            .addInterceptor(ReadOnlyInterceptor(allowLoginPost))
            .addInterceptor(RefusedSignInInterceptor())
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JellyfinApi::class.java)
    }

    private fun normalizeBaseUrl(baseUrl: String) =
        if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
}

class ReadOnlyInterceptor(private val allowLoginPost: Boolean) : Interceptor {

    companion object {
        private val READ_ONLY_METHODS = setOf("GET", "HEAD")

        // The sign-in requests (spec "Quick Connect sign-in", decision 2): by password, then
        // Quick Connect's code and its sign-in. None changes the library or the user.
        // QuickConnect/Authorize, which approves another device's code, isn't one.
        private val SIGN_IN_PATHS = listOf(
            "Users/AuthenticateByName",
            "QuickConnect/Initiate",
            "Users/AuthenticateWithQuickConnect"
        )

        /** Whether a [method] to [path] may go out; the sign-in client may also sign in. */
        fun allows(method: String, path: String, allowLoginPost: Boolean): Boolean {
            val verb = method.uppercase()
            if (verb in READ_ONLY_METHODS) return true
            return allowLoginPost && verb == "POST" &&
                SIGN_IN_PATHS.any { path.contains(it, ignoreCase = true) }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (allows(request.method, request.url.encodedPath, allowLoginPost)) {
            return chain.proceed(request)
        }
        throw ReadOnlyViolationException(
            "Blocked outgoing ${request.method} request to ${request.url} — " +
            "this app is read-only and must not modify the Jellyfin server."
        )
    }
}

class ReadOnlyViolationException(message: String) : SecurityException(message)
