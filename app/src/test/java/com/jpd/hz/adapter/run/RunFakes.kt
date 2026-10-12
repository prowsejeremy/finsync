package com.jpd.hz.adapter.run

import android.content.Context
import android.content.Intent
import com.jpd.hz.adapter.Availability
import com.jpd.hz.adapter.CatalogueResult
import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.ExtraFile
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.Platform
import com.jpd.hz.adapter.Source
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.SourceItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** A song for the fake sources: its bytes are its ID, so each file's content is known. */
fun song(
    id: String,
    path: String,
    fields: Map<String, String>? = mapOf("TITLE" to id),
    version: String? = "v1",
    kind: ItemKind = ItemKind.MUSIC
) = SourceItem(
    id = id,
    path = path,
    version = version,
    kind = kind,
    label = "Artist - $id",
    durationMs = null,
    size = null,
    fields = fields
)

fun group(id: String, kind: ChoiceKind, vararg itemIds: String) =
    ChoiceGroup(id, kind, name = id, detail = null, itemIds = itemIds.toList())

/**
 * A source whose catalogue and files the test sets. [failOpens] fails that many opens of an item
 * before one works; [images] are group images by group ID; [extraFiles] are its extras; [reader],
 * when set, gives each item's bytes in place of "bytes of <id>".
 */
class FakeSource(
    var catalogue: SourceCatalogue,
    private val reader: ((SourceItem) -> InputStream)? = null
) : Source {

    val opened = mutableListOf<String>()
    val failOpens = HashMap<String, Int>()
    var catalogueFails: String? = null
    val images = HashMap<String, String>()
    var extraFiles: List<Pair<String, String>> = emptyList()

    override val signInRefused: Flow<Boolean> = flowOf(false)

    override suspend fun checkAvailability() = Availability.AVAILABLE

    override suspend fun catalogue(): CatalogueResult =
        catalogueFails?.let { CatalogueResult.Failure(it) } ?: CatalogueResult.Success(catalogue)

    override suspend fun open(item: SourceItem): InputStream {
        opened.add(item.id)
        val failures = failOpens[item.id] ?: 0
        if (failures > 0) {
            failOpens[item.id] = failures - 1
            throw IOException("Source refused ${item.id}")
        }
        return reader?.invoke(item) ?: ByteArrayInputStream("bytes of ${item.id}".toByteArray())
    }

    override suspend fun openGroupImage(group: ChoiceGroup): InputStream? =
        images[group.id]?.let { ByteArrayInputStream(it.toByteArray()) }

    override fun extras(plan: SyncPlan): List<ExtraFile> =
        extraFiles.map { (path, text) ->
            ExtraFile(path) { ByteArrayInputStream(text.toByteArray()) }
        }
}

/** A platform with one connection, signed in until [signedIn] is false. */
class FakePlatform(
    private val source: FakeSource,
    override val key: String = "fake",
    sourceId: String = "one",
    override val choiceKinds: List<ChoiceKind> =
        listOf(ChoiceKind.ALBUM, ChoiceKind.PLAYLIST, ChoiceKind.BOOK)
) : Platform {

    val connection = Connection(key, sourceId, name = "fakeserver")
    var signedIn = true

    override val name: String = "Fake"
    override val needsNetwork: Boolean = false
    override val signInDetail: Int = 0

    override fun connections(): List<Connection> = if (signedIn) listOf(connection) else emptyList()

    override fun source(connection: Connection): Source = source

    override fun signInIntent(context: Context): Intent = Intent()

    override suspend fun clearSignIn(connection: Connection) {
        signedIn = false
    }
}
