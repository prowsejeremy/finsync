package com.jpd.hz.adapter.db

import androidx.room.Entity
import androidx.room.Index

/**
 * An item a connection's source offers, in its order ([position]), for the Sync card's counts and
 * the choice screens (spec "Data"). [kind] is an `ItemKind` name.
 */
@Entity(tableName = "catalogue_items", primaryKeys = ["connectionId", "itemId"])
data class CatalogueItem(
    val connectionId: String,
    val itemId: String,
    val position: Int,
    val kind: String,
    val label: String,
    val durationMs: Long?,
    val size: Long?
)

/** Something the user chooses, in the source's order. [kind] is a `ChoiceKind` key. */
@Entity(tableName = "catalogue_groups", primaryKeys = ["connectionId", "kind", "groupId"])
data class CatalogueGroup(
    val connectionId: String,
    val kind: String,
    val groupId: String,
    val position: Int,
    val name: String,
    val detail: String?
)

/** A group's item at [position]; a playlist may hold one item twice. */
@Entity(
    tableName = "catalogue_group_items",
    primaryKeys = ["connectionId", "kind", "groupId", "position"],
    indices = [Index(value = ["connectionId", "itemId"])]
)
data class CatalogueGroupItem(
    val connectionId: String,
    val kind: String,
    val groupId: String,
    val position: Int,
    val itemId: String
)

/** A group as a choice screen lists it: its items, and their size. */
data class GroupChoiceRow(
    val groupId: String,
    val name: String,
    val detail: String?,
    val itemCount: Int,
    val size: Long?
)
