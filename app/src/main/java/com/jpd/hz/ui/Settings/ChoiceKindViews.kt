package com.jpd.hz.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import com.jpd.hz.R
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.run.GroupChoice

// Each kind of choice's words: one `when` table, as Home's categories have.

/** The page's row title: "Albums to Sync". */
@get:StringRes
val ChoiceKind.rowTitleRes: Int
    get() = when (this) {
        ChoiceKind.ALBUM -> R.string.settings_albums_title
        ChoiceKind.PLAYLIST -> R.string.settings_playlists_title
        ChoiceKind.BOOK -> R.string.settings_books_title
        ChoiceKind.FOLDER -> R.string.settings_folders_title
    }

/** The page's row summary: "12 of 340 albums selected". */
@get:StringRes
val ChoiceKind.rowSummaryRes: Int
    get() = when (this) {
        ChoiceKind.ALBUM -> R.string.settings_albums_summary
        ChoiceKind.PLAYLIST -> R.string.settings_playlists_summary
        ChoiceKind.BOOK -> R.string.settings_books_summary
        ChoiceKind.FOLDER -> R.string.settings_folders_summary
    }

/** The choice screen's title: "Select Albums to Sync". */
@get:StringRes
val ChoiceKind.selectionTitleRes: Int
    get() = when (this) {
        ChoiceKind.ALBUM -> R.string.selection_title_albums
        ChoiceKind.PLAYLIST -> R.string.selection_title_playlists
        ChoiceKind.BOOK -> R.string.selection_title_books
        ChoiceKind.FOLDER -> R.string.selection_title_folders
    }

/**
 * A choice row's second line (spec "Screens"): an album "Daft Punk · 14 songs", a playlist
 * "24 songs", a book "Andy Weir · 412 MB", a folder "86 files".
 */
fun Resources.choiceDetail(kind: ChoiceKind, group: GroupChoice): String = when (kind) {
    ChoiceKind.ALBUM -> joinWithDots(listOfNotNull(group.detail, songs(group.itemCount)))
    ChoiceKind.PLAYLIST -> songs(group.itemCount)
    ChoiceKind.BOOK -> joinWithDots(
        listOfNotNull(
            group.detail ?: getString(R.string.unknown_author),
            group.size?.let(::formatFileSize)
        )
    )
    ChoiceKind.FOLDER ->
        getQuantityString(R.plurals.file_count, group.itemCount, group.itemCount)
}

private fun Resources.songs(count: Int): String =
    getQuantityString(R.plurals.song_count, count, count)
