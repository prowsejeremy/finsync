package com.jpd.hz.adapter

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes

private const val ID_SEPARATOR = ':'

/**
 * One kind of source, such as Jellyfin, Plex or a NAS share (spec H3, H6). The harness never
 * names one: the shell installs them at launch ([Platforms.install]).
 */
interface Platform {

    /** "jellyfin": the start of each connection's ID and `adapter_folders` entry. */
    val key: String

    /** "Jellyfin": the Adapters list, and D4's folder suffix, `kurage (Jellyfin)`. */
    val name: String

    /** What its users choose from, in the order its page lists them. */
    val choiceKinds: List<ChoiceKind>

    /** Whether its scheduled syncs wait for a network. */
    val needsNetwork: Boolean

    /** The signed-out card's line, such as "Sign in to sync from your Jellyfin server." */
    @get:StringRes
    val signInDetail: Int

    /** Its signed-in connections, from its own saved sign-ins. */
    fun connections(): List<Connection>

    fun source(connection: Connection): Source

    /** Its own sign-in screen, which returns to the page that opened it. */
    fun signInIntent(context: Context): Intent

    /** Clears [connection]'s saved sign-in, and nothing else (spec "Running connections"). */
    suspend fun clearSignIn(connection: Connection)
}

/**
 * One signed-in source (spec H3). Its [id], `<platform>:<sourceId>`, is the format
 * `adapter_folders` already saves. [name] names its folder and its row; [details] are the lines
 * its details sheet shows, such as the server's address and the user.
 */
data class Connection(
    val platform: String,
    val sourceId: String,
    val name: String,
    val details: List<String> = emptyList()
) {
    val id: String get() = idOf(platform, sourceId)

    companion object {
        fun idOf(platform: String, sourceId: String): String = "$platform$ID_SEPARATOR$sourceId"

        /** The platform key of a connection [id]. */
        fun platformOf(id: String): String = id.substringBefore(ID_SEPARATOR)
    }
}

/** The installed platforms: a list, not a registry (D12, spec H6). */
object Platforms {

    @Volatile
    private var installed: List<Platform> = emptyList()

    val all: List<Platform> get() = installed

    /** Called once by the shell, in `Hz.onCreate`, before any screen, service or worker. */
    fun install(platforms: List<Platform>) {
        installed = platforms
    }

    fun find(key: String): Platform? = installed.firstOrNull { it.key == key }

    /** The platform a connection [id] belongs to. */
    fun of(id: String): Platform? = find(Connection.platformOf(id))

    /** The connection [id] while it's signed in, else null. */
    fun connection(id: String): Connection? =
        of(id)?.connections()?.firstOrNull { it.id == id }

    /** Every signed-in connection, platform by platform. */
    fun connections(): List<Connection> = installed.flatMap { it.connections() }
}
