package com.jpd.hz.tags

/**
 * Our tags: the fields adapters write and the scanner reads, under TagLib's property names.
 * TagLib maps each one to the format's own frame or atom (TIT2 in ID3v2, ©nam in MP4 and so on).
 */
object TagField {
    const val TITLE = "TITLE"
    const val ARTIST = "ARTIST"
    const val ALBUM = "ALBUM"
    const val ALBUM_ARTIST = "ALBUMARTIST"
    const val GENRE = "GENRE"
    const val DATE = "DATE"
    const val TRACK_NUMBER = "TRACKNUMBER"
    const val DISC_NUMBER = "DISCNUMBER"

    /** The fields an adapter writes to a music track. */
    val MUSIC = listOf(TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, DATE, TRACK_NUMBER, DISC_NUMBER)

    /** The fields an adapter writes to a book. Books carry no track or disc number. */
    val BOOK = listOf(TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, DATE)
}

/** Joins several values in one field when writing (D6). Reading splits on ";" alone (D5). */
const val VALUE_SEPARATOR = "; "
