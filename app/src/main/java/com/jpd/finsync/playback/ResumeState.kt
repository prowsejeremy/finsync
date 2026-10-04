package com.jpd.finsync.playback

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName

private const val REPEAT_MODE_MAX = 2

/**
 * The saved queue. [itemIds] are in album (unshuffled) order and [index] points into them. On
 * restore, shuffle is re-applied with the saved track first.
 */
data class ResumeState(
    val itemIds: List<String>,
    val index: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffle: Boolean
)

// Nullable fields with fixed JSON names: Gson leaves missing fields null, and the existing
// @SerializedName keep rule stops R8 renaming them, so saves survive app updates.
private data class ResumeJson(
    @SerializedName("itemIds") val itemIds: List<String>?,
    @SerializedName("index") val index: Int?,
    @SerializedName("positionMs") val positionMs: Long?,
    @SerializedName("repeatMode") val repeatMode: Int?,
    @SerializedName("shuffle") val shuffle: Boolean?
)

private val gson = Gson()

fun encodeResumeState(state: ResumeState): String = gson.toJson(
    ResumeJson(state.itemIds, state.index, state.positionMs, state.repeatMode, state.shuffle)
)

/** Null for missing, malformed or inconsistent data, so a bad save can't break startup. */
fun decodeResumeState(json: String?): ResumeState? {
    if (json.isNullOrBlank()) return null
    val parsed = try {
        gson.fromJson(json, ResumeJson::class.java)
    } catch (e: JsonParseException) {
        return null
    } ?: return null
    val ids = parsed.itemIds?.takeIf { it.isNotEmpty() } ?: return null
    val index = parsed.index?.takeIf { it in ids.indices } ?: return null
    return ResumeState(
        itemIds = ids,
        index = index,
        positionMs = (parsed.positionMs ?: 0L).coerceAtLeast(0L),
        repeatMode = parsed.repeatMode?.takeIf { it in 0..REPEAT_MODE_MAX } ?: 0,
        shuffle = parsed.shuffle ?: false
    )
}

/**
 * Drops tracks that are no longer available. The saved track stays current with its position if
 * it's available; otherwise the next available track (or else the previous) starts from 0.
 */
fun ResumeState.keepOnly(available: Set<String>): ResumeState? {
    val kept = itemIds.map { it in available }
    val newIndex = remapStartIndex(kept, index) ?: return null
    return copy(
        itemIds = itemIds.filter { it in available },
        index = newIndex,
        positionMs = if (kept[index]) positionMs else 0L
    )
}
