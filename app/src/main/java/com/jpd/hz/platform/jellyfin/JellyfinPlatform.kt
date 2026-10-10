package com.jpd.hz.platform.jellyfin

import android.content.Context
import android.content.Intent
import com.jpd.hz.R
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.Platform
import com.jpd.hz.adapter.SignedOutSource
import com.jpd.hz.adapter.Source
import com.jpd.hz.model.ServerConfig
import com.jpd.hz.platform.jellyfin.ui.JellyfinSignInActivity

/** This platform's key: connection IDs and `adapter_folders` entries start with it. */
const val JELLYFIN = "jellyfin"

/**
 * Jellyfin (spec "Fits the harness"): one sign-in at a time, kept in `CredentialStore`, synced
 * as albums, playlists and books.
 */
class JellyfinPlatform(context: Context) : Platform {

    private val appContext = context.applicationContext
    private val repo = JellyfinRepository(appContext)

    override val key: String = JELLYFIN
    override val name: String = "Jellyfin"
    override val choiceKinds = listOf(ChoiceKind.ALBUM, ChoiceKind.PLAYLIST, ChoiceKind.BOOK)
    override val needsNetwork: Boolean = true
    override val signInDetail: Int = R.string.jellyfin_sign_in_detail

    override fun connections(): List<Connection> =
        listOfNotNull(repo.getSavedConfig()?.let(::connectionOf))

    // Signed out or in elsewhere since [connection] was read: a source that offers nothing, so
    // the run ends with a message instead of syncing another sign-in's server.
    override fun source(connection: Connection): Source {
        val config = repo.getSavedConfig()?.takeIf { it.serverId == connection.sourceId }
            ?: return SignedOutSource
        return JellyfinSource(appContext, config, repo)
    }

    override fun signInIntent(context: Context): Intent =
        Intent(context, JellyfinSignInActivity::class.java)

    override suspend fun clearSignIn(connection: Connection) {
        repo.logout(appContext)
    }

    companion object {
        fun connectionOf(config: ServerConfig) = Connection(
            platform = JELLYFIN,
            sourceId = config.serverId,
            name = config.serverName,
            details = listOf(config.serverUrl, config.username)
        )
    }
}
