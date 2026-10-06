package com.jpd.hz.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Anyone credited as an album artist or track artist; the first name seen for an ID wins. */
@Entity(tableName = "catalogue_artists")
data class CatalogueArtist(
    @PrimaryKey val artistId: String,
    val name: String
)
