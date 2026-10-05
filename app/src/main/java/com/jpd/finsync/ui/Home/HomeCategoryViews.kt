package com.jpd.finsync.ui

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.widget.ImageViewCompat
import com.jpd.finsync.R
import com.jpd.finsync.home.HomeCategory

/** How a Home category looks, where Home's card goes, and its count (spec "Home"). */
class HomeCategoryInfo(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    @IdRes val actionId: Int,
    val count: (HomeLibraryState.Ready) -> Int
)

/** The one table of each category's title, icon, Home action and count. */
val HomeCategory.info: HomeCategoryInfo
    get() = when (this) {
        HomeCategory.ALBUMS -> HomeCategoryInfo(
            R.string.home_albums, R.drawable.ic_album, R.id.action_home_to_albums
        ) { it.albumCount }
        HomeCategory.ALBUM_ARTISTS -> HomeCategoryInfo(
            R.string.home_album_artists, R.drawable.ic_person, R.id.action_home_to_album_artists
        ) { it.albumArtistCount }
        HomeCategory.GENRES -> HomeCategoryInfo(
            R.string.home_genres, R.drawable.ic_tag, R.id.action_home_to_genres
        ) { it.genreCount }
        HomeCategory.SONGS -> HomeCategoryInfo(
            R.string.home_songs, R.drawable.ic_music_note, R.id.action_home_to_songs
        ) { it.songCount }
        HomeCategory.PLAYLISTS -> HomeCategoryInfo(
            R.string.home_playlists, R.drawable.ic_queue, R.id.action_home_to_playlists
        ) { it.playlistCount }
        HomeCategory.AUDIO_BOOKS -> HomeCategoryInfo(
            R.string.home_audio_books, R.drawable.ic_book, R.id.action_home_to_audio_books
        ) { it.bookCount }
    }

/**
 * Shows [category]'s icon in the accent. Every icon is drawn white (ic_album too, since the
 * colour system) and takes its colour here.
 */
fun ImageView.showCategoryIcon(category: HomeCategory) {
    setImageResource(category.info.iconRes)
    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(context.accentColor()))
}
