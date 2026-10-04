package com.jpd.finsync.db

import androidx.room.TypeConverter
import com.google.gson.Gson

/** Stores a list of strings as a JSON array in one column. */
class StringListConverter {

    @TypeConverter
    fun fromList(values: List<String>): String = gson.toJson(values)

    @TypeConverter
    fun toList(json: String): List<String> =
        gson.fromJson(json, Array<String>::class.java)?.toList() ?: emptyList()

    private companion object {
        val gson = Gson()
    }
}
