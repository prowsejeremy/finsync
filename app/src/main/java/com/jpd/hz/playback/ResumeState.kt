package com.jpd.hz.playback

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName

private const val REPEAT_MODE_MAX = 2

/**
 * The saved queue (queue editing spec). [sourceIds] is the list it was played from, in that
 * list's order; [queue] lists positions in [sourceIds] in play order, each at most once; and
 * [index] is the playing track's position in [queue]. Restoring takes the queue as saved.
 */
data class ResumeState(
    val sourceIds: List<String>,
    val queue: List<Int>,
    val index: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffle: Boolean
)

// Nullable fields with fixed JSON names: Gson leaves missing fields null, and the existing
// @SerializedName keep rule stops R8 renaming them, so saves survive app updates.
private data class ResumeJson(
    @SerializedName("sourceIds") val sourceIds: List<String>?,
    @SerializedName("queue") val queue: List<Int?>?,
    @SerializedName("index") val index: Int?,
    @SerializedName("positionMs") val positionMs: Long?,
    @SerializedName("repeatMode") val repeatMode: Int?,
    @SerializedName("shuffle") val shuffle: Boolean?
)

private val gson = Gson()

fun encodeResumeState(state: ResumeState): String = gson.toJson(
    ResumeJson(
        state.sourceIds, state.queue, state.index, state.positionMs, state.repeatMode, state.shuffle
    )
)

/**
 * Null for missing, malformed or inconsistent data, so a bad save can't break startup. A save from
 * before queue editing has no source, so it's null too (no migration: one user).
 */
fun decodeResumeState(json: String?): ResumeState? {
    if (json.isNullOrBlank()) return null
    val parsed = try {
        gson.fromJson(json, ResumeJson::class.java)
    } catch (e: JsonParseException) {
        return null
    } ?: return null
    val ids = parsed.sourceIds?.takeIf { it.isNotEmpty() } ?: return null
    val queue = queueOrNull(parsed.queue, ids.size) ?: return null
    val index = parsed.index?.takeIf { it in queue.indices } ?: return null
    return ResumeState(
        sourceIds = ids,
        queue = queue,
        index = index,
        positionMs = (parsed.positionMs ?: 0L).coerceAtLeast(0L),
        repeatMode = parsed.repeatMode?.takeIf { it in 0..REPEAT_MODE_MAX } ?: 0,
        shuffle = parsed.shuffle ?: false
    )
}

/**
 * Drops source positions whose file is no longer available, drops their queue positions and
 * renumbers the rest. [available] holds one flag per source position, as the resolver returns
 * them, so a track listed twice is judged at each place. The playing track stays current with
 * its position if it's available; otherwise the next available track in the queue (or else the
 * previous) starts from 0.
 */
fun ResumeState.keepOnly(available: List<Boolean>): ResumeState? {
    require(available.size == sourceIds.size) { "One flag per source position" }
    // A kept source position's new number is the count of kept positions before it.
    val newPosition = available.runningFold(0) { count, kept -> if (kept) count + 1 else count }
    val keptInQueue = queue.map { available[it] }
    val newIndex = remapStartIndex(keptInQueue, index) ?: return null
    return copy(
        sourceIds = sourceIds.filterIndexed { position, _ -> available[position] },
        queue = queue.filter { available[it] }.map { newPosition[it] },
        index = newIndex,
        positionMs = if (keptInQueue[index]) positionMs else 0L
    )
}

// Gson keeps a JSON null inside a list as null, so a hand-edited save could hold one.
private fun queueOrNull(saved: List<Int?>?, sourceSize: Int): List<Int>? {
    val queue = saved?.filterNotNull() ?: return null
    val complete = queue.size == saved.size
    return queue.takeIf { complete && QueueOrder.isValidOrder(it, sourceSize) }
}
