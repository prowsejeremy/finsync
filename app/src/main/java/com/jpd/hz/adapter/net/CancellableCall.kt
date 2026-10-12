package com.jpd.hz.adapter.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

/**
 * [Call.execute], cancelled with its coroutine. A blocking call never sees its coroutine's
 * cancellation, so a Stop or a sign-out would wait for a server that may never answer: downloads
 * have no read timeout. It blocks the calling thread as execute does, so run it on an IO
 * dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Call.executeCancellable(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    try {
        val response = execute()
        // Cancelled just after it arrived: nobody reads it, so free its connection.
        continuation.resume(response) { response.close() }
    } catch (e: IOException) {
        continuation.resumeWithException(e)
    }
}
