package com.jpd.hz.library.scan

import com.jpd.hz.library.db.FileKind
import com.jpd.hz.tags.Chapter
import com.jpd.hz.tags.Normalising
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val ID = "file-1"

class FileReadingTest {

    private val stamp = FileStamp(size = 4_096, modifiedSec = 100, changedSec = 200, inode = 7)
    private val tags = FakeTagSource()
    private val decodeCalls = ArrayList<String>()
    private var decodes: Boolean? = true
    private val decode = DecodeCheck { path ->
        decodeCalls.add(path)
        decodes
    }

    private fun found(path: String) = FoundFile(path, File("/library", path))

    @Test
    fun aTrackKeepsItsTagValuesAndStamps() {
        tags.tagsByName["01 One.mp3"] = tagsOf(
            "TITLE" to "One More Time",
            "ARTIST" to "Daft Punk; Romanthony",
            "ALBUM" to "Discovery",
            "ALBUMARTIST" to "Daft Punk",
            "GENRE" to "House",
            "DATE" to "2001-03-12",
            "TRACKNUMBER" to "1/14",
            "DISCNUMBER" to "1",
            durationMs = 320_000L
        )

        val path = "Music/Daft Punk/Discovery/01 One.mp3"

        val scanned = readAudioFile(found(path), stamp, ID, tags, decode)

        val track = (scanned as ScannedFile.Track).track
        assertEquals(ID, track.trackId)
        assertEquals(ID, scanned.file.fileId)
        assertEquals(path, scanned.file.path)
        assertEquals("One More Time", track.title)
        assertEquals(listOf("Daft Punk", "Romanthony"), track.artistNames)
        assertEquals("Discovery", track.album)
        assertEquals(listOf("Daft Punk"), track.albumArtistNames)
        assertEquals(listOf("House"), track.genreNames)
        assertEquals(2001, track.year)
        assertEquals(1, track.trackNumber)
        assertEquals(1, track.discNumber)
        assertEquals(320_000L, track.durationMs)
        assertEquals("mp3", track.codec)
        assertEquals(128_000, track.bitrate)
        assertEquals(4_096L, track.size)
        assertEquals(Normalising.albumIdOf(listOf("Daft Punk"), "Discovery"), track.albumId)
        assertEquals(FileKind.TRACK, scanned.file.kind)
        assertEquals(stamp, FileStamp(scanned.file.size, scanned.file.modifiedSec,
            scanned.file.changedSec, scanned.file.inode))
        assertTrue(decodeCalls.isEmpty())
    }

    @Test
    fun aBookInAnAudiobooksFolderKeepsItsChapters() {
        tags.tagsByName["Hurry.m4b"] = tagsOf(
            "ALBUM" to "The Ruthless Elimination of Hurry",
            "ARTIST" to "John Mark Comer",
            codec = "aac",
            chapters = listOf(Chapter("", 0L), Chapter("Two", 60_000L))
        )

        val path = "kurage/Audiobooks/JMC/Hurry/Hurry.m4b"

        val scanned = readAudioFile(found(path), stamp, ID, tags, decode)

        val book = scanned as ScannedFile.Book
        assertEquals(FileKind.BOOK, book.file.kind)
        assertEquals(ID, book.book.bookId)
        assertEquals(listOf(ID), book.chapters.map { it.bookId }.distinct())
        assertEquals("The Ruthless Elimination of Hurry", book.book.title)
        assertEquals("John Mark Comer", book.book.author)
        assertEquals("aac", book.book.codec)
        assertNull(book.book.coverPath)
        assertEquals(listOf("Chapter 1", "Two"), book.chapters.map { it.name })
        assertEquals(listOf(0, 1), book.chapters.map { it.position })
        assertEquals(listOf(0L, 60_000L), book.chapters.map { it.startMs })
    }

    @Test
    fun aBookWithNoAuthorHasNone() {
        tags.tagsByName["b.mp3"] = tagsOf("TITLE" to "Story")

        val book = readAudioFile(found("Audiobooks/b.mp3"), stamp, ID, tags, decode)
            as ScannedFile.Book

        assertNull(book.book.author)
        assertEquals("Story", book.book.title)
        assertTrue(book.chapters.isEmpty())
    }

    @Test
    fun aFileTagLibCantOpenButBassCanIsTitledFromItsName() {
        val scanned = readAudioFile(found("Mine/07 Odd File.ogg"), stamp, ID, tags, decode)

        val track = (scanned as ScannedFile.Track).track
        assertEquals("07 Odd File", track.title)
        assertNull(track.album)
        assertNull(track.albumId)
        assertNull(track.durationMs)
        assertEquals(listOf("/library/Mine/07 Odd File.ogg"), decodeCalls)
    }

    @Test
    fun aFileNeitherCanOpenIsUnreadable() {
        decodes = false

        val scanned = readAudioFile(found("Mine/broken.mp3"), stamp, ID, tags, decode)

        assertTrue(scanned is ScannedFile.Unreadable)
        assertEquals(FileKind.UNREADABLE, scanned!!.file.kind)
        assertEquals("Mine/broken.mp3", scanned.file.path)
    }

    @Test
    fun aFileIsLeftForNextTimeWhenBassCantStart() {
        decodes = null

        assertNull(readAudioFile(found("Mine/broken.mp3"), stamp, ID, tags, decode))
    }

    @Test
    fun aKnownFileFoundAgainTakesItsIdAndNewPath() {
        tags.tagsByName["b.m4b"] = tagsOf("ALBUM" to "Hurry", chapters = listOf(Chapter("One", 0L)))
        val book = readAudioFile(found("Audiobooks/b.m4b"), stamp, ID, tags, decode)!!

        val moved = book.withIdentity("file-9", "Audiobooks/JMC/b.m4b") as ScannedFile.Book

        assertEquals("file-9", moved.file.fileId)
        assertEquals("Audiobooks/JMC/b.m4b", moved.file.path)
        assertEquals("file-9", moved.book.bookId)
        assertEquals(listOf("file-9"), moved.chapters.map { it.bookId })
    }

    @Test
    fun identityKeysNameTheBookOrTheSongButNotAFileWithoutAnAlbum() {
        tags.tagsByName["b.m4b"] = tagsOf("ALBUM" to "Hurry", "ARTIST" to "John Mark Comer")
        tags.tagsByName["c.m4b"] = tagsOf("ALBUM" to " hurry ", "ARTIST" to "JOHN MARK COMER")
        tags.tagsByName["01.mp3"] = tagsOf(
            "TITLE" to "One", "ALBUM" to "Discovery", "ALBUMARTIST" to "Daft Punk",
            "TRACKNUMBER" to "1"
        )
        tags.tagsByName["02.mp3"] = tagsOf("TITLE" to "Loose")

        fun keyOf(path: String) =
            readAudioFile(found(path), stamp, ID, tags, decode)!!.identityKey()

        assertEquals(keyOf("Audiobooks/b.m4b"), keyOf("Audiobooks/Copy/c.m4b"))
        assertNotEquals(keyOf("Audiobooks/b.m4b"), keyOf("Music/01.mp3"))
        assertNull(keyOf("Music/02.mp3"))
        decodes = false
        assertNull(keyOf("Music/broken.mp3"))
    }
}
