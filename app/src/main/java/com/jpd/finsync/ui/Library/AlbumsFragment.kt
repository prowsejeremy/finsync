package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentAlbumsBinding
import com.jpd.finsync.databinding.ItemAlbumBinding
import com.jpd.finsync.library.AlbumSummary

class AlbumsFragment : Fragment() {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AlbumsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.header.tvTitle.setText(R.string.header_albums)
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }

        val adapter = AlbumsAdapter { album -> openAlbum(album.albumId) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewModel.albums.observe(viewLifecycleOwner) { albums ->
            adapter.submitList(albums)
            binding.tvEmpty.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openAlbum(albumId: String) = navigateSafely(
        R.id.albumsFragment,
        R.id.action_albums_to_album,
        bundleOf(ARG_ALBUM_ID to albumId)
    )
}

/** Downloads' row layout, with "2018 · 13 tracks" where Downloads shows sync status. */
private class AlbumsAdapter(
    private val onClick: (AlbumSummary) -> Unit
) : ListAdapter<AlbumSummary, AlbumsAdapter.Holder>(AlbumDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemAlbumBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(album: AlbumSummary) {
            val resources = binding.root.resources
            val count = album.downloadedTrackCount
            binding.tvAlbum.text = album.name
            binding.tvArtist.text =
                album.albumArtist ?: resources.getString(R.string.unknown_artist)
            binding.tvSyncStatus.text = joinWithDots(
                listOf(
                    album.year?.toString(),
                    resources.getQuantityString(R.plurals.track_count, count, count)
                )
            )
            loadArtwork(binding.ivAlbumArt, album.artworkPath)
            binding.root.setOnClickListener { onClick(album) }
        }
    }
}

private object AlbumDiff : DiffUtil.ItemCallback<AlbumSummary>() {
    override fun areItemsTheSame(oldItem: AlbumSummary, newItem: AlbumSummary) =
        oldItem.albumId == newItem.albumId

    override fun areContentsTheSame(oldItem: AlbumSummary, newItem: AlbumSummary) =
        oldItem == newItem
}
