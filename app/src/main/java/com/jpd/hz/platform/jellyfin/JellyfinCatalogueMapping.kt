package com.jpd.hz.platform.jellyfin

import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.NO_ALBUM_GROUP
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.SourceItem
import com.jpd.hz.platform.jellyfin.api.MediaItem
import com.jpd.hz.platform.jellyfin.api.ServerCatalogue

private const val TICKS_PER_MS = 10_000L
private const val AUDIO_TYPE = "Audio"
private const val VERSION_SEPARATOR = "|"
private const val UNKNOWN_ALBUM = "Unknown Album"

/**
 * Jellyfin's fetch in the harness's terms (spec "Fits the harness"), with no Android imports:
 * audio and audiobook items, and album, playlist and book groups. [noAlbumName] names the group of
 * songs with no album ("Songs with no album").
 */
object JellyfinCatalogueMapping {

    fun sourceCatalogueOf(fetched: ServerCatalogue, noAlbumName: String): SourceCatalogue {
        val music = fetched.audio.map(::itemOf)
        val books = fetched.books.map(::itemOf)
        return SourceCatalogue(
            items = music + books,
            groups = albumsOf(fetched.audio, noAlbumName) + playlistsOf(fetched) +
                fetched.books.map(::bookGroupOf),
            failedKinds = buildSet {
                if (fetched.playlistsFailed) add(ChoiceKind.PLAYLIST)
                if (fetched.booksFailed) add(ChoiceKind.BOOK)
            },
            failedGroupIds = fetched.failedPlaylistIds
        )
    }

    fun itemOf(item: MediaItem): SourceItem {
        val book = JellyfinLayout.isBook(item)
        return SourceItem(
            id = item.id,
            path = JellyfinLayout.pathOf(item),
            version = listOfNotNull(item.path, item.dateModified)
                .joinToString(VERSION_SEPARATOR)
                .ifEmpty { null },
            kind = if (book) ItemKind.BOOK else ItemKind.MUSIC,
            label = JellyfinLayout.labelOf(item),
            durationMs = item.runTimeTicks?.let { it / TICKS_PER_MS },
            size = item.mediaSources?.firstOrNull()?.size,
            fields = JellyfinTagMapping.fieldsOf(item)
        )
    }

    // Each album once, in the order its first song comes; songs with no album make one more.
    private fun albumsOf(audio: List<MediaItem>, noAlbumName: String): List<ChoiceGroup> {
        val albums = LinkedHashMap<String, MutableList<MediaItem>>()
        audio.forEach { albums.getOrPut(it.albumId ?: NO_ALBUM_GROUP) { ArrayList() }.add(it) }
        return albums.map { (albumId, songs) ->
            val first = songs.first()
            val noAlbum = albumId == NO_ALBUM_GROUP
            ChoiceGroup(
                id = albumId,
                kind = ChoiceKind.ALBUM,
                name = if (noAlbum) noAlbumName else first.album ?: UNKNOWN_ALBUM,
                detail = if (noAlbum) null else first.albumArtist ?: first.artists?.firstOrNull(),
                itemIds = songs.map { it.id }
            )
        }
    }

    // A playlist with no audio entries isn't an audio playlist, so it's left out (3b).
    private fun playlistsOf(fetched: ServerCatalogue): List<ChoiceGroup> =
        fetched.playlists.mapNotNull { playlist ->
            val audio = playlist.entries.filter { it.type == AUDIO_TYPE }
            if (audio.isEmpty()) return@mapNotNull null
            ChoiceGroup(
                id = playlist.playlist.id,
                kind = ChoiceKind.PLAYLIST,
                name = playlist.playlist.name,
                detail = null,
                itemIds = audio.map { it.id }
            )
        }

    private fun bookGroupOf(book: MediaItem) = ChoiceGroup(
        id = book.id,
        kind = ChoiceKind.BOOK,
        name = book.name,
        detail = JellyfinLayout.bookAuthorOf(book),
        itemIds = listOf(book.id)
    )
}
