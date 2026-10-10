package com.jpd.hz.platform.jellyfin

import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ExtraFile
import com.jpd.hz.adapter.run.keepSetOf
import com.jpd.hz.adapter.run.planOf
import com.jpd.hz.adapter.run.playlistFileNamesOf
import com.jpd.hz.adapter.run.playlistTextOf
import com.jpd.hz.adapter.files.LibraryLayout
import com.jpd.hz.platform.jellyfin.api.MediaItem
import com.jpd.hz.tags.TagFingerprint
import org.junit.Test

/**
 * What the harness makes of the fixed Jellyfin catalogue (adapter harness spec, "Testing" 1). The
 * golden files were written by the sync code before the harness, so this proves Jellyfin's paths,
 * keep set, tags, playlist files and artist photos are unchanged. The only differences allowed
 * are H4's: an empty choice means none, and songs with no album plan under `all` or their own
 * group. Neither scenario relies on the old empty-means-every-album rule.
 */
class JellyfinGoldenTest {

    @Test
    fun `the harness matches the golden files`() {
        for (scenario in JellyfinGoldenFixture.scenarios) {
            GoldenText.assertMatches(scenario.file, render(scenario))
        }
    }

    private fun render(scenario: JellyfinGoldenFixture.Scenario): String {
        val fetched = JellyfinGoldenFixture.catalogue
        val catalogue = JellyfinCatalogueMapping.sourceCatalogueOf(fetched, "Songs with no album")
        val chosen = mapOf(
            ChoiceKind.ALBUM to scenario.albumIds,
            ChoiceKind.PLAYLIST to scenario.playlistIds,
            ChoiceKind.BOOK to scenario.bookIds
        )
        val plan = planOf(catalogue.groups, catalogue.items, chosen)
        val planned = plan.items.map { item ->
            val fields = item.fields.orEmpty()
            GoldenText.PlannedFile(item.id, item.path, TagFingerprint.of(fields), fields)
        }
        val byId: Map<String, MediaItem> = (fetched.audio + fetched.books).associateBy { it.id }
        val photos = JellyfinLayout.artistPhotosOf(plan.items.mapNotNull { byId[it.id] }
            .filterNot(JellyfinLayout::isBook))
        val extras = photos.keys.map { ExtraFile(it) { null } }
        val names = playlistFileNamesOf(plan)
        val playlistFiles = plan.playlists.associate { playlist ->
            LibraryLayout.playlistFilePath(names.getValue(playlist.group.id)) to
                playlistTextOf(playlist)
        }
        return GoldenText.render(planned, keepSetOf(plan, extras), playlistFiles, photos)
    }
}
