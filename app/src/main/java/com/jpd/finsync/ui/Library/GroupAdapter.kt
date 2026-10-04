package com.jpd.finsync.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jpd.finsync.R
import com.jpd.finsync.databinding.ItemGroupAlbumBinding
import com.jpd.finsync.databinding.ItemGroupAllSongsBinding
import com.jpd.finsync.databinding.ItemGroupLabelBinding
import com.jpd.finsync.library.AlbumSummary

private const val VIEW_TYPE_ALL_SONGS = 0
private const val VIEW_TYPE_LABEL = 1
private const val VIEW_TYPE_ALBUM = 2

/**
 * The group page's list below Play and Shuffle: All songs, the ALBUMS label and the albums.
 * [showAlbumArtist] is for genre pages; on an artist page every album is theirs.
 */
class GroupAdapter(
    private val showAlbumArtist: Boolean,
    private val onAllSongsClick: () -> Unit,
    private val onAlbumClick: (AlbumSummary) -> Unit
) : ListAdapter<GroupRow, RecyclerView.ViewHolder>(GroupRowDiff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is GroupRow.AllSongs -> VIEW_TYPE_ALL_SONGS
        GroupRow.AlbumsLabel -> VIEW_TYPE_LABEL
        is GroupRow.Album -> VIEW_TYPE_ALBUM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_ALL_SONGS ->
                AllSongsHolder(ItemGroupAllSongsBinding.inflate(inflater, parent, false))
            VIEW_TYPE_LABEL -> LabelHolder(ItemGroupLabelBinding.inflate(inflater, parent, false))
            else -> AlbumHolder(ItemGroupAlbumBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is GroupRow.AllSongs -> (holder as AllSongsHolder).bind(row)
            GroupRow.AlbumsLabel -> Unit
            is GroupRow.Album -> (holder as AlbumHolder).bind(row.album)
        }
    }

    private inner class AllSongsHolder(private val binding: ItemGroupAllSongsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: GroupRow.AllSongs) {
            val covers = binding.covers
            bindStackedCovers(
                covers.ivCoverFront, covers.ivCoverMiddle, covers.ivCoverBack, row.coverPaths
            )
            binding.tvSummary.text = songsSummary(binding.root.resources, row.songs)
            binding.root.setOnClickListener { onAllSongsClick() }
        }
    }

    private class LabelHolder(binding: ItemGroupLabelBinding) :
        RecyclerView.ViewHolder(binding.root)

    private inner class AlbumHolder(private val binding: ItemGroupAlbumBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(album: AlbumSummary) {
            val resources = binding.root.resources
            val count = album.downloadedTrackCount
            binding.tvAlbum.text = album.name
            binding.tvArtist.visibility = if (showAlbumArtist) View.VISIBLE else View.GONE
            binding.tvArtist.text =
                album.albumArtist ?: resources.getString(R.string.unknown_artist)
            binding.tvMeta.text = joinWithDots(
                listOf(
                    album.year?.toString(),
                    resources.getQuantityString(R.plurals.track_count, count, count)
                )
            )
            loadArtwork(binding.ivAlbumArt, album.artworkPath)
            binding.root.setOnClickListener { onAlbumClick(album) }
        }
    }
}

private object GroupRowDiff : DiffUtil.ItemCallback<GroupRow>() {
    override fun areItemsTheSame(oldItem: GroupRow, newItem: GroupRow): Boolean = when {
        oldItem is GroupRow.Album && newItem is GroupRow.Album ->
            oldItem.album.albumId == newItem.album.albumId
        else -> oldItem::class == newItem::class
    }

    override fun areContentsTheSame(oldItem: GroupRow, newItem: GroupRow) = oldItem == newItem
}
