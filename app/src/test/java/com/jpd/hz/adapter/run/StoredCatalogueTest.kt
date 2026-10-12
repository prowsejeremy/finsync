package com.jpd.hz.adapter.run

import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.SourceItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a fetch stores: a part that didn't load keeps its previous rows, so cleanup deletes
 * nothing it may own (adapter harness spec, "The sync run", step 3).
 */
class StoredCatalogueTest {

    private fun group(id: String, kind: ChoiceKind, name: String, vararg itemIds: String) =
        ChoiceGroup(id, kind, name, null, itemIds.toList())

    private fun sourceItem(id: String, label: String = id) =
        SourceItem(id, "Music/$id.flac", null, ItemKind.MUSIC, label, null, null, null)

    private fun stored(id: String, kind: ItemKind = ItemKind.MUSIC, label: String = id) =
        StoredItem(id, kind, label, null, null)

    private val previous = StoredCatalogue(
        groups = listOf(
            group("alb1", ChoiceKind.ALBUM, "Old album", "t1"),
            group("p1", ChoiceKind.PLAYLIST, "Old one", "t1"),
            group("p2", ChoiceKind.PLAYLIST, "Old two", "t2"),
            group("b1", ChoiceKind.BOOK, "Dune", "b1")
        ),
        items = listOf(stored("t1"), stored("t2"), stored("b1", ItemKind.BOOK))
    )

    private fun fresh(failedKinds: Set<ChoiceKind> = emptySet(), failedGroupIds: Set<String>) =
        SourceCatalogue(
            items = listOf(sourceItem("t9")),
            groups = listOf(
                group("alb9", ChoiceKind.ALBUM, "New album", "t9"),
                group("p1", ChoiceKind.PLAYLIST, "New one", "t9")
            ),
            failedKinds = failedKinds,
            failedGroupIds = failedGroupIds
        )

    @Test
    fun `with nothing failed, only the fresh catalogue is stored`() {
        val merged = mergeFailedParts(fresh(failedGroupIds = emptySet()), previous)

        assertEquals(listOf("alb9", "p1"), merged.groups.map { it.id })
        assertEquals(listOf(stored("t9")), merged.items)
    }

    @Test
    fun `a group whose entries didn't load keeps its previous group and items`() {
        val merged = mergeFailedParts(fresh(failedGroupIds = setOf("p2")), previous)

        assertEquals(listOf("New album", "New one", "Old two"), merged.groups.map { it.name })
        assertEquals(listOf("t9", "t2"), merged.items.map { it.id })
    }

    @Test
    fun `a kind that didn't load keeps every previous group of that kind and their items`() {
        val merged = mergeFailedParts(
            fresh(failedKinds = setOf(ChoiceKind.BOOK), failedGroupIds = setOf("p2")),
            previous
        )

        assertEquals(
            listOf("New album", "New one", "Old two", "Dune"),
            merged.groups.map { it.name }
        )
        assertEquals(listOf("t9", "t2", "b1"), merged.items.map { it.id })
        assertEquals(ItemKind.BOOK, merged.items.last().kind)
    }

    @Test
    fun `a kind that didn't load gives way to its previous groups, even if some came`() {
        // A source that reports a kind failed yet sends part of it: the previous rows stand for
        // the whole kind, as before the harness.
        val merged = mergeFailedParts(
            fresh(failedKinds = setOf(ChoiceKind.PLAYLIST), failedGroupIds = emptySet()),
            previous
        )

        assertEquals(
            listOf("New album", "Old one", "Old two"),
            merged.groups.filter { it.kind != ChoiceKind.BOOK }.map { it.name }
        )
    }

    @Test
    fun `when the playlist list itself didn't load, every playlist keeps its rows`() {
        val nothingFresh = SourceCatalogue(
            items = emptyList(),
            groups = emptyList(),
            failedKinds = setOf(ChoiceKind.PLAYLIST)
        )

        val merged = mergeFailedParts(nothingFresh, previous)

        assertEquals(
            previous.groups.filter { it.kind == ChoiceKind.PLAYLIST },
            merged.groups
        )
        assertEquals(listOf("t1", "t2"), merged.items.map { it.id })
    }

    @Test
    fun `kept groups go after the fresh ones`() {
        val merged = mergeFailedParts(
            fresh(failedKinds = setOf(ChoiceKind.BOOK), failedGroupIds = emptySet()),
            previous
        )

        assertEquals(listOf("alb9", "p1", "b1"), merged.groups.map { it.id })
    }

    @Test
    fun `an item the fresh catalogue lists keeps its fresh details, once`() {
        val renamed = SourceCatalogue(
            items = listOf(sourceItem("t2", label = "Fresh label")),
            groups = emptyList(),
            failedGroupIds = setOf("p2")
        )

        val merged = mergeFailedParts(renamed, previous)

        assertEquals(listOf(stored("t2", label = "Fresh label")), merged.items)
    }
}
