package com.jpd.hz.adapter

import com.jpd.hz.adapter.run.SyncPlan
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * The group ID of a server's songs with no album (spec H4): one album group, so `all` still plans
 * them and a specific selection can tick them.
 */
const val NO_ALBUM_GROUP = "no-album"

/**
 * One connection's source, as the harness's sync run reads it (spec "The contract"). The platform
 * owns the read side: what's there, where each item lands, its bytes, its tags and its extras.
 * The harness owns everything that happens on the phone.
 */
interface Source {

    /** Whether the source answers, and takes the saved sign-in. */
    suspend fun checkAvailability(): Availability

    /** True while the source refuses the saved sign-in, as it learns of it from any request. */
    val signInRefused: Flow<Boolean>

    /** Everything the source offers. A failure ends the run with its message. */
    suspend fun catalogue(): CatalogueResult

    /**
     * The original file's bytes. Throws when the source can't give them. A Stop or a sign-out
     * cancels it, which must end any wait for them (over HTTP, Call.executeCancellable).
     */
    suspend fun open(item: SourceItem): InputStream

    /** An album's, book's or playlist's cover, or null when it has none. */
    suspend fun openGroupImage(group: ChoiceGroup): InputStream?

    /** Any other file the source adds beside the planned items, such as artist photos. */
    fun extras(plan: SyncPlan): List<ExtraFile>
}

enum class Availability { AVAILABLE, UNREACHABLE, SIGN_IN_REFUSED }

sealed class CatalogueResult {
    data class Success(val catalogue: SourceCatalogue) : CatalogueResult()

    data class Failure(val message: String) : CatalogueResult()
}

/**
 * What a source offers: its items in its own order, which plans keep, and the groups of them the
 * user chooses from. A kind whose list didn't load ([failedKinds]), or a group whose entries
 * didn't ([failedGroupIds]), keeps its previous rows, so cleanup never deletes files it may own.
 */
data class SourceCatalogue(
    val items: List<SourceItem>,
    val groups: List<ChoiceGroup>,
    val failedKinds: Set<ChoiceKind> = emptySet(),
    val failedGroupIds: Set<String> = emptySet()
)

/**
 * One file. [path] is relative to the connection's folder, with `/` separators. [version] changes
 * when the source's file does, which downloads it again. Null [fields] keep the file's own tags.
 */
data class SourceItem(
    val id: String,
    val path: String,
    val version: String?,
    val kind: ItemKind,
    val label: String,
    val durationMs: Long?,
    val size: Long?,
    val fields: Map<String, String>?
)

/** Music or a book: the Sync card counts them apart, and books plan last. */
enum class ItemKind { MUSIC, BOOK }

/** Something the user chooses: an album, a playlist, a book or a folder. */
data class ChoiceGroup(
    val id: String,
    val kind: ChoiceKind,
    val name: String,
    val detail: String?,
    val itemIds: List<String>
)

/**
 * The kinds of choice (spec H4).
 * - [selectAllSavesAll]: Select all saves `all`, so groups added later follow; otherwise it ticks
 *   the listed groups, so a new book never downloads by itself (3b).
 * - [coverBesideItems]: a `folder.jpg` is kept beside each planned item, and the group's image is
 *   fetched into its first planned item's folder.
 * - [planOrder]: albums and folders plan first, then songs only playlists need, then books.
 */
enum class ChoiceKind(
    val key: String,
    val selectAllSavesAll: Boolean,
    val coverBesideItems: Boolean,
    val planOrder: Int
) {
    ALBUM("album", selectAllSavesAll = true, coverBesideItems = true, planOrder = 0),
    FOLDER("folder", selectAllSavesAll = true, coverBesideItems = false, planOrder = 0),
    PLAYLIST("playlist", selectAllSavesAll = false, coverBesideItems = false, planOrder = 1),
    BOOK("book", selectAllSavesAll = false, coverBesideItems = true, planOrder = 2);

    companion object {
        fun fromKey(key: String): ChoiceKind? = values().firstOrNull { it.key == key }
    }
}

/** A file the source adds at [path], relative to the connection's folder; null means none. */
data class ExtraFile(val path: String, val open: suspend () -> InputStream?)
