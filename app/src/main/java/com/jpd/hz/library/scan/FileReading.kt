package com.jpd.hz.library.scan

import com.jpd.hz.library.db.FileKind
import com.jpd.hz.library.db.LibraryBook
import com.jpd.hz.library.db.LibraryBookChapter
import com.jpd.hz.library.db.LibraryFile
import com.jpd.hz.library.db.LibraryTrack
import com.jpd.hz.tags.AudioDetails
import com.jpd.hz.tags.BookChapters
import com.jpd.hz.tags.FileTags
import com.jpd.hz.tags.Normalising
import com.jpd.hz.tags.TagReading

// Separates the parts of an identity key; no tag holds it.
private const val KEY_SEPARATOR = "\u001F"

/** One audio file as the scanner stores it: its file row, plus what it turned out to be. */
sealed class ScannedFile {
    abstract val file: LibraryFile

    /** The same rows under [fileId] and [path]: a known file found again (A2). */
    fun withIdentity(fileId: String, path: String): ScannedFile {
        val found = file.copy(fileId = fileId, path = path)
        return when (this) {
            is Track -> Track(found, track.copy(trackId = fileId))
            is Book ->
                Book(found, book.copy(bookId = fileId), chapters.map { it.copy(bookId = fileId) })
            is Unreadable -> Unreadable(found)
        }
    }

    /**
     * What recognises this file when its path and stamp have both changed (A2): a book's authors
     * and title; a song's album, disc, track number and title. Null when the tags can't tell
     * files apart: an unreadable file, or a song with no album.
     */
    fun identityKey(): String? = when (this) {
        is Book -> keyOf("book", book.author.orEmpty(), book.title)
        is Track -> track.albumId?.let { albumId ->
            keyOf(
                "track",
                albumId,
                track.discNumber?.toString().orEmpty(),
                track.trackNumber?.toString().orEmpty(),
                track.title
            )
        }
        is Unreadable -> null
    }

    private fun keyOf(vararg parts: String): String =
        parts.joinToString(KEY_SEPARATOR) { Normalising.normalise(it) }

    data class Track(override val file: LibraryFile, val track: LibraryTrack) : ScannedFile()

    data class Book(
        override val file: LibraryFile,
        val book: LibraryBook,
        val chapters: List<LibraryBookChapter>
    ) : ScannedFile()

    /** Neither TagLib nor BASS could open it. It's counted, and not read again until it changes. */
    data class Unreadable(override val file: LibraryFile) : ScannedFile()
}

/**
 * Reads one new or changed file (spec "Our tags", "Errors") into rows under [fileId]. When TagLib
 * can't open it, BASS decides: a file BASS can play is added, titled from its file name; any other
 * is unreadable. Null when BASS couldn't start, so nothing is stored and the next pass tries
 * again.
 */
fun readAudioFile(
    found: FoundFile,
    stamp: FileStamp,
    fileId: String,
    tags: TagSource,
    decode: DecodeCheck
): ScannedFile? {
    val name = ScanRules.fileNameOf(found.path)
    val fileTags = tags.read(found.file.path, ScanRules.extensionOf(name))
    val isBook = ScanRules.isBook(found.path)
    if (fileTags == null) {
        when (decode.canDecode(found.file.path)) {
            null -> return null
            false ->
                return ScannedFile.Unreadable(
                    fileRow(fileId, found.path, stamp, FileKind.UNREADABLE)
                )
            true -> Unit
        }
    }
    val fields = fileTags?.fields.orEmpty()
    val audio = fileTags?.audio
    return if (isBook) {
        ScannedFile.Book(
            file = fileRow(fileId, found.path, stamp, FileKind.BOOK),
            book = bookRow(fileId, name, fields, audio, stamp.size),
            chapters = chapterRows(fileId, fileTags)
        )
    } else {
        ScannedFile.Track(
            file = fileRow(fileId, found.path, stamp, FileKind.TRACK),
            track = trackRow(fileId, name, fields, audio, stamp.size)
        )
    }
}

private fun fileRow(fileId: String, path: String, stamp: FileStamp, kind: FileKind) = LibraryFile(
    fileId = fileId,
    path = path,
    size = stamp.size,
    modifiedSec = stamp.modifiedSec,
    changedSec = stamp.changedSec,
    inode = stamp.inode,
    kind = kind
)

private fun trackRow(
    fileId: String,
    fileName: String,
    fields: Map<String, List<String>>,
    audio: AudioDetails?,
    size: Long
): LibraryTrack {
    val track = TagReading.track(fields, fileName)
    return LibraryTrack(
        trackId = fileId,
        title = track.title,
        artistNames = track.artists,
        album = track.album,
        albumArtistNames = track.albumArtists,
        genreNames = track.genres,
        year = track.year,
        discNumber = track.discNumber,
        trackNumber = track.trackNumber,
        durationMs = audio?.durationMs,
        codec = audio?.codec,
        bitDepth = audio?.bitDepth,
        sampleRate = audio?.sampleRate,
        bitrate = audio?.bitrate,
        size = size,
        albumId = Normalising.albumIdOf(track.albumArtists, track.album)
    )
}

private fun bookRow(
    fileId: String,
    fileName: String,
    fields: Map<String, List<String>>,
    audio: AudioDetails?,
    size: Long
): LibraryBook {
    val book = TagReading.book(fields, fileName)
    return LibraryBook(
        bookId = fileId,
        title = book.title,
        author = book.author.takeIf { book.authors.isNotEmpty() },
        durationMs = audio?.durationMs,
        codec = audio?.codec,
        bitDepth = audio?.bitDepth,
        sampleRate = audio?.sampleRate,
        bitrate = audio?.bitrate,
        size = size,
        // Set by the scan from the files beside it (LibraryDerivation).
        coverPath = null,
        embeddedCover = null
    )
}

// In start order. A blank name becomes "Chapter N", as the server's chapters did.
private fun chapterRows(fileId: String, fileTags: FileTags?): List<LibraryBookChapter> {
    if (fileTags == null) return emptyList()
    return BookChapters.of(fileTags).mapIndexed { position, chapter ->
        LibraryBookChapter(
            bookId = fileId,
            position = position,
            name = chapter.name.takeIf { it.isNotBlank() } ?: "Chapter ${position + 1}",
            startMs = chapter.startMs
        )
    }
}
