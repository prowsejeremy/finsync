package com.jpd.finsync.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jpd.finsync.model.ServerConfig

/**
 * Stores Jellyfin credentials securely using EncryptedSharedPreferences.
 */
class CredentialStore(context: Context) {

    companion object {
        private const val PREF_FILE       = "jellyfin_creds"
        private const val KEY_SERVER_URL  = "server_url"
        private const val KEY_USER_ID     = "user_id"
        private const val KEY_TOKEN       = "access_token"
        private const val KEY_USERNAME    = "username"
        private const val KEY_SERVER_ID   = "server_id"
        private const val KEY_SERVER_NAME = "server_name"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_SERVER_URL,  config.serverUrl)
            .putString(KEY_USER_ID,     config.userId)
            .putString(KEY_TOKEN,       config.accessToken)
            .putString(KEY_USERNAME,    config.username)
            .putString(KEY_SERVER_ID,   config.serverId)
            .putString(KEY_SERVER_NAME, config.serverName)
            .apply()
    }

    fun load(): ServerConfig? {
        val url   = prefs.getString(KEY_SERVER_URL,  null) ?: return null
        val uid   = prefs.getString(KEY_USER_ID,     null) ?: return null
        val token = prefs.getString(KEY_TOKEN,       null) ?: return null
        return ServerConfig(
            serverUrl   = url,
            userId      = uid,
            accessToken = token,
            username    = prefs.getString(KEY_USERNAME,    "") ?: "",
            serverId    = prefs.getString(KEY_SERVER_ID,   "") ?: "",
            serverName  = prefs.getString(KEY_SERVER_NAME, "") ?: ""
        )
    }

    fun clear() = prefs.edit().clear().apply()

    fun isLoggedIn() = prefs.getString(KEY_TOKEN, null) != null
}
