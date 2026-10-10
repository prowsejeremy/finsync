package com.jpd.hz.adapter.run

import android.util.Log
import com.jpd.hz.adapter.Source
import com.jpd.hz.adapter.ExtraFile
import com.jpd.hz.adapter.files.AdapterFiles
import com.jpd.hz.adapter.files.LibraryLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

private const val TAG = "Extras"

/**
 * The end of a run, after cleanup (spec "The sync run", step 8): each album's and book's missing
 * cover, beside its first planned item; each missing extra, such as an artist photo; each planned
 * playlist's missing cover; and the playlist files. Covers and extras are fetched only beside
 * files on the phone. The keep set lists every one, so cleanup removes those nothing needs. A
 * failure is logged and retried next run, and never fails it.
 */
internal object Extras {

    suspend fun run(folder: File, source: Source, plan: SyncPlan, extras: List<ExtraFile>) {
        quietly("Covers") {
            for (cover in plan.covers) {
                // Beside the first item the run could write; never outside the folder.
                val first = cover.items.firstOrNull { isSafePath(it.path) } ?: continue
                val target = File(folder, LibraryLayout.coverBeside(first.path))
                if (isMissingBesideFiles(target)) {
                    save({ source.openGroupImage(cover.group) }, target)
                }
            }
        }
        quietly("Extras") {
            for (extra in extras) {
                if (!isSafePath(extra.path)) continue
                val target = File(folder, extra.path)
                if (isMissingBesideFiles(target)) save(extra.open, target)
            }
        }
        val names = playlistFileNamesOf(plan)
        quietly("Playlist covers") {
            for (playlist in plan.playlists) {
                val name = names[playlist.group.id] ?: continue
                val target = File(folder, LibraryLayout.playlistCoverPath(name))
                if (!isFile(target)) save({ source.openGroupImage(playlist.group) }, target)
            }
        }
        quietly("Playlist files") {
            withContext(Dispatchers.IO) {
                for (playlist in plan.playlists) {
                    val name = names[playlist.group.id] ?: continue
                    val target = File(folder, LibraryLayout.playlistFilePath(name))
                    try {
                        AdapterFiles.writeIfChanged(target, playlistTextOf(playlist))
                    } catch (e: IOException) {
                        Log.w(TAG, "Couldn't write playlist ${playlist.group.id}", e)
                    }
                }
            }
        }
    }

    private suspend fun quietly(what: String, step: suspend () -> Unit) {
        try {
            step()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what skipped", e)
        }
    }

    // Only beside files on the phone, and only once: a cover is fetched when it's missing.
    private suspend fun isMissingBesideFiles(target: File): Boolean =
        withContext(Dispatchers.IO) { target.parentFile?.isDirectory == true && !target.isFile }

    private suspend fun isFile(target: File): Boolean =
        withContext(Dispatchers.IO) { target.isFile }

    // Written beside the target and renamed last, so a half-written image never shows. None (a
    // null stream) writes nothing; a failure is logged.
    private suspend fun save(open: suspend () -> InputStream?, target: File) {
        val stream = try {
            open()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't fetch ${target.name}", e)
            null
        } ?: return
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            val part = AdapterFiles.partOf(target)
            val written = stream.use { input -> part.outputStream().use { input.copyTo(it) } }
            if (written == 0L) {
                part.delete()
                return@withContext
            }
            if (!part.renameTo(target)) {
                part.delete()
                throw IOException("Couldn't save ${target.name}")
            }
        }
    }
}
