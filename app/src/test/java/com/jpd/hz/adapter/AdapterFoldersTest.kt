package com.jpd.hz.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `adapter_folders` format and D4's folder rules. */
class AdapterFoldersTest {

    private val kurage = AdapterFolder("jellyfin", "3f2a", "/storage/emulated/0/Media/hz/kurage")
    private val other = AdapterFolder("jellyfin", "77b0", "/storage/emulated/0/Media/hz/kurage (Jellyfin)")

    private fun choose(
        saved: List<AdapterFolder> = emptyList(),
        serverId: String = "3f2a",
        legacy: String? = null,
        withFiles: Set<String> = emptySet(),
        serverName: String = "kurage"
    ) = AdapterFolders.chooseFolder(
        saved = saved,
        adapter = "jellyfin",
        serverId = serverId,
        legacy = legacy,
        library = "/storage/emulated/0/Media/hz",
        serverName = serverName,
        platform = "Jellyfin",
        hasFiles = { it in withFiles }
    )

    @Test
    fun `entries save one per line and read back, even with separators in the path`() {
        val odd = AdapterFolder("jellyfin", "9c", "/storage/emulated/0/Media/a|b:c")
        val text = AdapterFolders.format(listOf(kurage, odd))

        assertEquals(
            "jellyfin:3f2a|/storage/emulated/0/Media/hz/kurage\n" +
                "jellyfin:9c|/storage/emulated/0/Media/a|b:c",
            text
        )
        assertEquals(listOf(kurage, odd), AdapterFolders.parse(text))
    }

    @Test
    fun `lines that don't parse are skipped, and nothing saved reads as no entries`() {
        val text = "no separator\n:3f2a|/a\njellyfin:|/a\njellyfin|/a\njellyfin:3f2a| \n" +
            AdapterFolders.format(listOf(kurage))

        assertEquals(listOf(kurage), AdapterFolders.parse(text))
        assertTrue(AdapterFolders.parse(null).isEmpty())
        assertTrue(AdapterFolders.parse("").isEmpty())
    }

    @Test
    fun `saving a server's folder replaces its entry and keeps the others`() {
        val moved = kurage.copy(path = "/storage/emulated/0/Music/kurage")

        val folders = AdapterFolders.withFolder(listOf(kurage, other), moved)

        assertEquals(listOf(other, moved), folders)
        assertEquals(moved, AdapterFolders.find(folders, "jellyfin", "3f2a"))
        assertNull(AdapterFolders.find(folders, "plex", "3f2a"))
    }

    @Test
    fun `a taken name gets the platform, then a number`() {
        val taken = mutableSetOf<String>()
        fun name() = AdapterFolders.nameFor("kurage", "Jellyfin") { it in taken }

        assertEquals("kurage", name())
        taken += "kurage"
        assertEquals("kurage (Jellyfin)", name())
        taken += "kurage (Jellyfin)"
        assertEquals("kurage (Jellyfin 2)", name())
        taken += "kurage (Jellyfin 2)"
        assertEquals("kurage (Jellyfin 3)", name())
    }

    @Test
    fun `a server name is made safe, never hidden, and never empty`() {
        fun name(server: String) = AdapterFolders.nameFor(server, "Jellyfin") { false }

        assertEquals("AC_DC Server", name(" AC/DC Server "))
        assertEquals("Jellyfin", name(".."))
        assertEquals("hidden", name(".hidden"))
        assertEquals("Jellyfin", name("   "))
    }

    @Test
    fun `a saved folder wins, even over the old sync folder`() {
        assertEquals(kurage.path, choose(saved = listOf(kurage), legacy = "/storage/emulated/0/Media/hz"))
    }

    @Test
    fun `an install with nothing saved keeps the folder it synced to before`() {
        assertEquals(
            "/storage/emulated/0/Media/hz",
            choose(legacy = "/storage/emulated/0/Media/hz")
        )
    }

    @Test
    fun `a new server gets its name under the library, unless that folder holds anything`() {
        assertEquals("/storage/emulated/0/Media/hz/kurage", choose())
        assertEquals(
            "/storage/emulated/0/Media/hz/kurage (Jellyfin)",
            choose(withFiles = setOf("/storage/emulated/0/Media/hz/kurage"))
        )
    }

    @Test
    fun `a second server with the same name doesn't share the first one's folder`() {
        val first = kurage.copy(path = "/storage/emulated/0/Media/hz/Kurage")

        assertEquals(
            "/storage/emulated/0/Media/hz/kurage (Jellyfin)",
            choose(saved = listOf(first), serverId = "77b0", legacy = "/storage/emulated/0/Media/hz")
        )
    }

    @Test
    fun `when the library is a server's folder, a new server goes beside it, never inside`() {
        val whole = kurage.copy(path = "/storage/emulated/0/Media/hz")

        assertEquals(
            "/storage/emulated/0/Media/kurage",
            choose(saved = listOf(whole), serverId = "77b0")
        )
        assertEquals(
            "/storage/emulated/0/Media/kurage (Jellyfin)",
            choose(
                saved = listOf(whole),
                serverId = "77b0",
                withFiles = setOf("/storage/emulated/0/Media/kurage")
            )
        )
        assertEquals(
            "/storage/emulated/0/Media/HZ (Jellyfin)",
            choose(saved = listOf(whole), serverId = "77b0", serverName = "HZ")
        )
    }

    @Test
    fun `a new server's folder never holds another server's`() {
        val nested = kurage.copy(path = "/storage/emulated/0/Media/hz/kurage/Jellyfin")

        assertEquals(
            "/storage/emulated/0/Media/hz/kurage (Jellyfin)",
            choose(saved = listOf(nested), serverId = "77b0")
        )
    }
}
