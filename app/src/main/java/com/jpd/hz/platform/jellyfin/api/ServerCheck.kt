package com.jpd.hz.platform.jellyfin.api

import java.net.HttpURLConnection.HTTP_MULT_CHOICE
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

/**
 * What the connection check found (spec "Sign-in health", decision 3), from `GET Users/Me`,
 * which needs the sign-in: connected; the sign-in refused, on a server that answered; or offline.
 */
enum class ServerCheck {
    CONNECTED, REFUSED, OFFLINE;

    val reachable: Boolean get() = this != OFFLINE

    companion object {
        /** From the response's [code], or null when no response came. */
        fun from(code: Int?): ServerCheck = when (code) {
            null -> OFFLINE
            in HTTP_OK until HTTP_MULT_CHOICE -> CONNECTED
            HTTP_UNAUTHORIZED -> REFUSED
            else -> OFFLINE
        }
    }
}
