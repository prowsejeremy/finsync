package com.jpd.finsync.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jpd.finsync.R
import com.jpd.finsync.databinding.ItemAlbumTrackBinding
import com.jpd.finsync.databinding.ItemDiscHeadingBinding

private const val VIEW_TYPE_DISC = 0
private const val VIEW_TYPE_TRACK = 1

/** Album detail's track list; the playing track is drawn in accent green. */
class AlbumTracksAdapter(
    private val onTrackClick: (AlbumListRow.Track) -> Unit
) : ListAdapter<AlbumListRow, RecyclerView.ViewHolder>(RowDiff) {

    private var playingItemId: String? = null

    fun setPlayingItemId(itemId: String?) {
        if (itemId == playingItemId) return
        playingItemId = itemId
        // Rows are few (one album), so a full rebind is simpler than tracking two positions.
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AlbumListRow.DiscHeading -> VIEW_TYPE_DISC
        is AlbumListRow.Track -> VIEW_TYPE_TRACK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_DISC) {
            DiscHolder(ItemDiscHeadingBinding.inflate(inflater, parent, false))
        } else {
            TrackHolder(ItemAlbumTrackBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is AlbumListRow.DiscHeading -> (holder as DiscHolder).bind(row)
            is AlbumListRow.Track -> (holder as TrackHolder).bind(row)
        }
    }

    private class DiscHolder(private val binding: ItemDiscHeadingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: AlbumListRow.DiscHeading) {
            binding.tvDisc.text =
                binding.root.resources.getString(R.string.disc_heading, row.discNumber)
        }
    }

    private inner class TrackHolder(private val binding: ItemAlbumTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: AlbumListRow.Track) {
            val context = binding.root.context
            val playing = row.itemId == playingItemId
            val accent = context.accentColor()
            val titleColour =
                if (playing) accent else ContextCompat.getColor(context, R.color.text_primary)
            val numberColour =
                if (playing) accent else ContextCompat.getColor(context, R.color.muted)
            binding.tvNumber.text = row.number
            binding.tvNumber.setTextColor(numberColour)
            binding.tvTitle.text = row.title
            binding.tvTitle.setTextColor(titleColour)
            binding.tvTrackArtists.text = row.artists
            binding.tvTrackArtists.visibility = if (row.artists != null) View.VISIBLE else View.GONE
            binding.tvDuration.text = row.durationMs?.let(::formatDuration) ?: ""
            binding.root.setOnClickListener { onTrackClick(row) }
        }
    }
}

private object RowDiff : DiffUtil.ItemCallback<AlbumListRow>() {
    override fun areItemsTheSame(oldItem: AlbumListRow, newItem: AlbumListRow): Boolean =
        when {
            oldItem is AlbumListRow.Track && newItem is AlbumListRow.Track ->
                oldItem.itemId == newItem.itemId
            oldItem is AlbumListRow.DiscHeading && newItem is AlbumListRow.DiscHeading ->
                oldItem.discNumber == newItem.discNumber
            else -> false
        }

    override fun areContentsTheSame(oldItem: AlbumListRow, newItem: AlbumListRow) =
        oldItem == newItem
}
