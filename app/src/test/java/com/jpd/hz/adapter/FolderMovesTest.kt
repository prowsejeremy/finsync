package com.jpd.hz.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val ROOT = "/storage/emulated/0"

class FolderMovesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val nothingTaken: (String) -> Boolean = { false }

    @Test
    fun anAdapterFolderInsideTheNewLibraryStays() {
        assertEquals(
            FolderMove.Keep,
            FolderMoves.moveFor("$ROOT/Media/hz/kurage", "$ROOT/Media", "Jellyfin", nothingTaken)
        )
        assertEquals(
            FolderMove.Keep,
            FolderMoves.moveFor("$ROOT/media/HZ/kurage", "$ROOT/Media/hz", "Jellyfin", nothingTaken)
        )
    }

    @Test
    fun aLibraryInsideTheAdapterFolderIsRefused() {
        listOf("$ROOT/Media/hz", "$ROOT/Media/hz/Music", "$ROOT/media/hz/").forEach { library ->
            assertEquals(
                library,
                FolderMove.Refuse,
                FolderMoves.moveFor("$ROOT/Media/hz", library, "Jellyfin", nothingTaken)
            )
        }
    }

    @Test
    fun anAdapterFolderElsewhereMovesUnderItsOwnNameWithTheSuffixRule() {
        assertEquals(
            FolderMove.MoveTo("$ROOT/Music/kurage"),
            FolderMoves.moveFor("$ROOT/Media/hz/kurage", "$ROOT/Music", "Jellyfin", nothingTaken)
        )
        assertEquals(
            FolderMove.MoveTo("$ROOT/Music/kurage (Jellyfin)"),
            FolderMoves.moveFor("$ROOT/Media/hz/kurage", "$ROOT/Music/", "Jellyfin") {
                it == "$ROOT/Music/kurage"
            }
        )
    }

    @Test
    fun aFolderInADefaultLibraryKeepsItsParentAsTheLibrary() {
        assertEquals(
            FirstLibrary.Parent("$ROOT/Media/hz"),
            FolderMoves.firstLibrary(
                "$ROOT/Media/hz/kurage",
                listOf("$ROOT/Media/hz/"),
                "kurage",
                "Jellyfin",
                nothingTaken
            )
        )
    }

    @Test
    fun aPickedFolderBecomesTheLibraryWithItsContentMovingIntoTheServersFolder() {
        assertEquals(
            FirstLibrary.MoveInto("$ROOT/Media/hz", "$ROOT/Media/hz/kurage"),
            FolderMoves.firstLibrary(
                "$ROOT/Media/hz/", listOf("$ROOT/Media/hz"), "kurage", "Jellyfin", nothingTaken
            )
        )
        assertEquals(
            FirstLibrary.MoveInto("$ROOT/tmp", "$ROOT/tmp/kurage (Jellyfin)"),
            FolderMoves.firstLibrary("$ROOT/tmp", listOf("$ROOT/Media/hz"), "kurage", "Jellyfin") {
                it == "$ROOT/tmp/kurage"
            }
        )
    }

    @Test
    fun renamesMoveEachExistingFolder() {
        val from = folderWith("hz", "Music/a.mp3", "Audiobooks/b.m4b")
        val to = File(from, "kurage")
        val renames = listOf("Music", "Audiobooks", "Playlists")
            .map { File(from, it) to File(to, it) }

        val outcome = FolderMoves.renameAll(renames)

        assertEquals(RenameResult.DONE, outcome.result)
        assertEquals(renames.take(2), outcome.done)
        assertTrue(File(to, "Music/a.mp3").isFile)
        assertTrue(File(to, "Audiobooks/b.m4b").isFile)
        assertFalse(File(from, "Music").exists())
    }

    @Test
    fun aFailedRenamePutsTheOthersBack() {
        val from = folderWith("hz", "Music/a.mp3", "Audiobooks/b.m4b")
        val to = folderWith("hz/kurage", "Audiobooks/already.m4b")
        val renames = listOf("Music", "Audiobooks").map { File(from, it) to File(to, it) }

        val outcome = FolderMoves.renameAll(renames)

        assertEquals(RenameResult.UNDONE, outcome.result)
        assertEquals(emptyList<Pair<File, File>>(), outcome.done)
        assertTrue(File(from, "Music/a.mp3").isFile)
        assertTrue(File(from, "Audiobooks/b.m4b").isFile)
        assertTrue(File(to, "Audiobooks/already.m4b").isFile)
        assertFalse(File(to, "Music").exists())
    }

    @Test
    fun aPendingMoveSurvivesBeingSaved() {
        val move = PendingMove(
            library = "$ROOT/Media/hz",
            folders = listOf(AdapterFolder("jellyfin", "id-1", "$ROOT/Media/hz/kurage (Jellyfin)")),
            renames = listOf(
                "$ROOT/Media/hz/Music" to "$ROOT/Media/hz/kurage (Jellyfin)/Music",
                "$ROOT/Media/hz/Audiobooks" to "$ROOT/Media/hz/kurage (Jellyfin)/Audiobooks"
            )
        )

        assertEquals(move, FolderMoves.decode(FolderMoves.encode(move)))
        val unsaved = PendingMove(null, move.folders, emptyList(), recordsFrom = "$ROOT/Media/hz")
        assertEquals(unsaved, FolderMoves.decode(FolderMoves.encode(unsaved)))
        val keepOnly = PendingMove("$ROOT/Media", emptyList(), emptyList())
        assertEquals(keepOnly, FolderMoves.decode(FolderMoves.encode(keepOnly)))
    }

    @Test
    fun anythingElseIsNoPendingMove() {
        assertNull(FolderMoves.decode(null))
        assertNull(FolderMoves.decode(""))
        assertNull(FolderMoves.decode("rename\tonly-one-path"))
        assertNull(FolderMoves.decode("folder\tjellyfin\tid"))
        assertNull(FolderMoves.decode("something\telse"))
    }

    @Test
    fun savedFoldersResolveAgainstTheLibraryUnlessTheyreFullPaths() {
        assertEquals("$ROOT/Media/hz/kurage", FolderMoves.resolve("kurage", "$ROOT/Media/hz/"))
        assertEquals("$ROOT/Media/hz", FolderMoves.resolve("$ROOT/Media/hz", "$ROOT/Media"))
        assertEquals("kurage", FolderMoves.relativeOf("$ROOT/Media/hz/kurage/", "$ROOT/Media/hz"))
        // Shared storage ignores case; the path keeps its own spelling.
        assertEquals("HZ/kurage", FolderMoves.relativeOf("$ROOT/media/HZ/kurage", "$ROOT/Media"))
        assertNull(FolderMoves.relativeOf("$ROOT/Media/hz", "$ROOT/Media/hz"))
        assertNull(FolderMoves.relativeOf("$ROOT/Music/kurage", "$ROOT/Media/hz"))
    }

    @Test
    fun aFolderHoldingMostOfTheRecordedFilesAndLittleElseIsTheAdaptersFolder() {
        val folder = folderWith("moved", "Music/a.mp3", "Music/b.mp3", "Music/folder.jpg")
        val sizes = mapOf(
            "Music/a.mp3" to File(folder, "Music/a.mp3").length(),
            "Music/b.mp3" to File(folder, "Music/b.mp3").length()
        )

        assertTrue(FolderMoves.holdsRecords(folder, sizes))
        assertFalse(FolderMoves.holdsRecords(folder, sizes + ("Music/c.mp3" to 1L)))
        assertFalse(FolderMoves.holdsRecords(folder, sizes.mapValues { it.value + 1 }))
        assertFalse(FolderMoves.holdsRecords(folder, emptyMap()))
        // The same files among plenty of others: someone's own music, which cleanup would empty.
        folderWith("moved", "Music/c.mp3", "Music/d.mp3")
        assertFalse(FolderMoves.holdsRecords(folder, sizes))
    }

    @Test
    fun theSearchFindsTheFirstMatchingFolderAndLooksBelowMisses() {
        folderWith("root/A/kurage", "Music/a.mp3")
        folderWith("root/A/kurage", "Music/mine.mp3")
        folderWith("root/B/C/real", "Music/a.mp3")
        val records = mapOf("Music/a.mp3" to File(temp.root, "root/B/C/real/Music/a.mp3").length())

        val found = FolderMoves.findAdapterFolder(File(temp.root, "root"), records)

        assertEquals("root/B/C/real", found?.relativeTo(temp.root)?.path)
        assertNull(FolderMoves.findAdapterFolder(File(temp.root, "root"), mapOf("x.mp3" to 1L)))
    }

    @Test
    fun hasFilesCountsAnythingButAMissingOrEmptyFolder() {
        val empty = temp.newFolder("empty")
        val full = folderWith("full", "x.txt")

        assertFalse(hasFiles(File(temp.root, "missing").path))
        assertFalse(hasFiles(empty.path))
        assertTrue(hasFiles(full.path))
        assertTrue(hasFiles(File(full, "x.txt").path))
    }

    private fun folderWith(name: String, vararg files: String): File {
        val folder = File(temp.root, name)
        files.forEach { path ->
            File(folder, path).apply {
                parentFile?.mkdirs()
                writeText(path)
            }
        }
        folder.mkdirs()
        return folder
    }
}
