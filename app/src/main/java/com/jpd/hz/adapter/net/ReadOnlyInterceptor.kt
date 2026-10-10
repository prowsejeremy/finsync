package com.jpd.hz.adapter.net

import okhttp3.Interceptor
import okhttp3.Response

private val READ_ONLY_METHODS = setOf("GET", "HEAD")

/**
 * hz never changes anything on a server (spec "The contract"): every HTTP adapter's clients add
 * this. It lets GET and HEAD through, and a POST only to one of [allowedPosts], the platform's own
 * sign-in requests, matched anywhere in the path ignoring case. Anything else throws.
 */
class ReadOnlyInterceptor(private val allowedPosts: List<String> = emptyList()) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (allows(request.method, request.url.encodedPath, allowedPosts)) {
            return chain.proceed(request)
        }
        throw ReadOnlyViolationException(
            "Blocked outgoing ${request.method} request to ${request.url} — " +
                "hz is read-only and must not modify the server."
        )
    }

    companion object {
        /** Whether a [method] to [path] may go out. */
        fun allows(method: String, path: String, allowedPosts: List<String>): Boolean {
            val verb = method.uppercase()
            if (verb in READ_ONLY_METHODS) return true
            return verb == "POST" && allowedPosts.any { path.contains(it, ignoreCase = true) }
        }
    }
}

class ReadOnlyViolationException(message: String) : SecurityException(message)
