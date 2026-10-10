package com.jpd.hz.api

import java.net.HttpURLConnection.HTTP_MULT_CHOICE
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK

/**
 * What asking a Jellyfin server for a Quick Connect code gave (spec "Quick Connect sign-in",
 * decision 7): the code to show and the secret that checks it, Quick Connect off on the server,
 * or a failure.
 */
sealed class QuickConnectStart {
    data class Started(val code: String, val secret: String) : QuickConnectStart()

    object Off : QuickConnectStart()

    data class Failed(val message: String) : QuickConnectStart()
}

/**
 * One check on a shown code (decision 5), from `GET QuickConnect/Connect`: approved, still
 * waiting, expired (the server forgets an unapproved code after about 10 minutes), or failed.
 */
enum class QuickConnectPoll {
    APPROVED, WAITING, EXPIRED, FAILED;

    companion object {
        /** From the response's [code] and its `Authenticated`; a null code is no response. */
        fun from(code: Int?, authenticated: Boolean?): QuickConnectPoll = when (code) {
            null -> FAILED
            in HTTP_OK until HTTP_MULT_CHOICE -> if (authenticated == true) APPROVED else WAITING
            HTTP_NOT_FOUND -> EXPIRED
            else -> FAILED
        }
    }
}
