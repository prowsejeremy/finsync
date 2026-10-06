package com.jpd.hz.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalogue_genres")
data class CatalogueGenre(
    @PrimaryKey val genreId: String,
    val name: String
)
