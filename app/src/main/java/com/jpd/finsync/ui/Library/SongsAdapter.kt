package com.jpd.finsync.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jpd.finsync.R
import com.jpd.finsync.databinding.ItemSongBinding
import com.jpd.finsync.library.SongRow

/** Songs rows show art and "artists · album"; All songs rows show just the album. */
enum class SongListStyle { SONGS, ALL_SONGS }

/** Songs and All songs; the playing track is drawn in accent green. */
class SongsAdapter(
    private val style: SongListStyle,
    private val onSongClick: (SongRow) -> Unit
) : ListAdapter<SongRow, SongsAdapter.Holder>(SongDiff) {

    private var playingItemId: String? = null

    fun setPlayingItemId(itemId: String?) {
        if (itemId == playingItemId) return
        val previous = playingItemId
        playingItemId = itemId
        // Songs can hold thousands of rows, so only the two rows that change are rebound.
        notifyRowChanged(previous)
        notifyRowChanged(itemId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    private fun notifyRowChanged(itemId: String?) {
        if (itemId == null) return
        val position = currentList.indexOfFirst { it.itemId == itemId }
        if (position >= 0) notifyItemChanged(position)
    }

    inner class Holder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(song: SongRow) {
            val context = binding.root.context
            val playing = song.itemId == playingItemId
            val titleColour = if (playing) {
                context.accentColor()
            } else {
                ContextCompat.getColor(context, R.color.text_primary)
            }
            val showsArt = style == SongListStyle.SONGS
            val subtitle = if (showsArt) {
                joinWithDots(listOf(song.artists, song.albumName))
            } else {
                song.albumName.orEmpty()
            }
            binding.tvTitle.text = song.title
            binding.tvTitle.setTextColor(titleColour)
            binding.tvSubtitle.text = subtitle
            binding.tvSubtitle.visibility = if (subtitle.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvDuration.text = song.durationMs?.let(::formatDuration) ?: ""
            binding.artCard.visibility = if (showsArt) View.VISIBLE else View.GONE
            if (showsArt) loadArtwork(binding.ivAlbumArt, song.artworkPath)
            binding.root.setOnClickListener { onSongClick(song) }
        }
    }
}

private object SongDiff : DiffUtil.ItemCallback<SongRow>() {
    override fun areItemsTheSame(oldItem: SongRow, newItem: SongRow) =
        oldItem.itemId == newItem.itemId

    override fun areContentsTheSame(oldItem: SongRow, newItem: SongRow) = oldItem == newItem
}
