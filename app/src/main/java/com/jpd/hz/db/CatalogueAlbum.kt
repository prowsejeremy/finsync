package com.jpd.hz.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** An album in the server's library, whether or not any of it is downloaded. */
@Entity(tableName = "catalogue_albums")
data class CatalogueAlbum(
    @PrimaryKey val albumId: String,
    val name: String,
    val albumArtist: String?,
    val year: Int?
)
