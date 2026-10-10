package com.jpd.hz.sync

import com.jpd.hz.adapter.PlaylistFiles
import com.jpd.hz.library.playlistRowsFrom
import com.jpd.hz.tags.TagFingerprint
import org.junit.Test
import java.io.File

private const val SYNC_FOLDER = "/sync"

/**
 * What today's Jellyfin sync makes of a fixed catalogue (adapter harness spec, "Testing" 1). The
 * golden files were written from this code before the harness work began; the harness must give
 * the same text.
 */
class JellyfinGoldenTest {

    @Test
    fun `today's sync matches the golden files`() {
        for (scenario in JellyfinGoldenFixture.scenarios) {
            GoldenText.assertMatches(scenario.file, render(scenario))
        }
    }

    private fun render(scenario: JellyfinGoldenFixture.Scenario): String {
        val catalogue = JellyfinGoldenFixture.catalogue
        val selection =
            SyncSelection(scenario.albumIds, scenario.playlistIds, scenario.bookIds)
        val plan = syncPlanOf(catalogue, playlistRowsFrom(catalogue.playlists), selection)
        val planned = plan.items.map { item ->
            val fields = JellyfinTagMapping.fieldsOf(item)
            GoldenText.PlannedFile(
                itemId = item.id,
                path = syncRelativePath(item),
                fingerprint = TagFingerprint.of(fields),
                fields = fields
            )
        }
        val keep = filesToKeep(File(SYNC_FOLDER), plan).map { it.removePrefix("$SYNC_FOLDER/") }
        val names = playlistFileNamesOf(plan)
        val playlistFiles = plan.playlists.associate { playlist ->
            val fileName = names.getValue(playlist.playlistId)
            playlistFilePath(fileName) to
                PlaylistFiles.contentOf(playlist.name, playlist.items.map(::playlistEntryOf))
        }
        return GoldenText.render(planned, keep, playlistFiles, artistPhotosOf(plan.tracks))
    }
}
