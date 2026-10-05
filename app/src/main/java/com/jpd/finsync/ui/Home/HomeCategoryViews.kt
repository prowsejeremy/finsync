package com.jpd.finsync.ui

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.jpd.finsync.R
import com.jpd.finsync.home.HomeCategory

/** How a Home category looks, where Home's card goes, and its count (spec "Home"). */
class HomeCategoryInfo(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    /** False only for Albums: ic_album has its own colours. */
    val tinted: Boolean,
    @IdRes val actionId: Int,
    val count: (HomeLibraryState.Ready) -> Int
)

/** The one table of each category's title, icon, tint, Home action and count. */
val HomeCategory.info: HomeCategoryInfo
    get() = when (this) {
        HomeCategory.ALBUMS -> HomeCategoryInfo(
            R.string.home_albums, R.drawable.ic_album, false, R.id.action_home_to_albums
        ) { it.albumCount }
        HomeCategory.ALBUM_ARTISTS -> HomeCategoryInfo(
            R.string.home_album_artists, R.drawable.ic_person, true,
            R.id.action_home_to_album_artists
        ) { it.albumArtistCount }
        HomeCategory.GENRES -> HomeCategoryInfo(
            R.string.home_genres, R.drawable.ic_tag, true, R.id.action_home_to_genres
        ) { it.genreCount }
        HomeCategory.SONGS -> HomeCategoryInfo(
            R.string.home_songs, R.drawable.ic_music_note, true, R.id.action_home_to_songs
        ) { it.songCount }
        HomeCategory.PLAYLISTS -> HomeCategoryInfo(
            R.string.home_playlists, R.drawable.ic_queue, true, R.id.action_home_to_playlists
        ) { it.playlistCount }
        HomeCategory.AUDIO_BOOKS -> HomeCategoryInfo(
            R.string.home_audio_books, R.drawable.ic_book, true, R.id.action_home_to_audio_books
        ) { it.bookCount }
    }

/** Shows [category]'s icon, tinted accent_green unless it has its own colours. */
fun ImageView.showCategoryIcon(category: HomeCategory) {
    val info = category.info
    setImageResource(info.iconRes)
    // Cleared for Albums, so a recycled row doesn't keep another category's tint.
    val tint =
        if (info.tinted) ContextCompat.getColorStateList(context, R.color.accent_green) else null
    ImageViewCompat.setImageTintList(this, tint)
}
