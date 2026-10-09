package com.jpd.hz.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.net.HttpURLConnection.HTTP_MULT_CHOICE
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

private const val AUTHORIZATION = "Authorization"

/**
 * Whether the server refuses the saved sign-in (spec "Sign-in health", decision 2): the token of
 * the last request that carried one and got a 401, until a request with it succeeds. Screens
 * compare it with the saved sign-in's token, so a late 401 from an old sign-in never marks a new
 * one, and signing in or out needs no reset.
 */
class SignInStatus {

    private val _refusedToken = MutableStateFlow<String?>(null)
    val refusedToken: StateFlow<String?> = _refusedToken.asStateFlow()

    /** One response's [code], for a request whose `Authorization` header was [authorization]. */
    fun record(code: Int, authorization: String?) {
        val token = authorization?.let(JellyfinClient::tokenOf) ?: return
        when (code) {
            HTTP_UNAUTHORIZED -> _refusedToken.value = token
            in HTTP_OK until HTTP_MULT_CHOICE -> _refusedToken.compareAndSet(token, null)
        }
    }

    companion object {
        /** The app's: every OkHttp client hz builds reports to it. */
        val shared = SignInStatus()

        /** True while the server refuses [token], the saved sign-in's. */
        fun isRefused(refusedToken: String?, token: String?): Boolean =
            token != null && refusedToken == token
    }
}

/** Reports each response to [status]. Added to every OkHttp client hz builds. */
class RefusedSignInInterceptor(
    private val status: SignInStatus = SignInStatus.shared
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        status.record(response.code, request.header(AUTHORIZATION))
        return response
    }
}
