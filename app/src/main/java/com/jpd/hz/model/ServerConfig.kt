package com.jpd.hz.model

/**
 * Jellyfin's saved sign-in. It stays in this package because `auth/CredentialStore.kt`, which
 * agents can't read or edit, imports it (adapter harness spec, H8); the boundary test counts it
 * as Jellyfin's.
 */
data class ServerConfig(
    val serverUrl: String,
    val userId: String,
    val accessToken: String,
    val username: String,
    val serverId: String,
    val serverName: String
)
