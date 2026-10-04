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
import com.jpd.finsync.databinding.FragmentPlaylistsBinding
import com.jpd.finsync.databinding.ItemPlaylistBinding
import com.jpd.finsync.library.PlaylistSummary

/** Selected playlists with a downloaded song, A–Z (spec "Playlists"). */
class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlaylistsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.header.tvTitle.setText(R.string.header_playlists)
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }

        val adapter = PlaylistsAdapter { playlist -> openPlaylist(playlist.playlistId) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            adapter.submitList(playlists)
            binding.emptyState.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openPlaylist(playlistId: String) = navigateSafely(
        R.id.playlistsFragment,
        R.id.action_playlists_to_playlist,
        bundleOf(ARG_PLAYLIST_ID to playlistId)
    )
}

private class PlaylistsAdapter(
    private val onClick: (PlaylistSummary) -> Unit
) : ListAdapter<PlaylistSummary, PlaylistsAdapter.Holder>(PlaylistDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(playlist: PlaylistSummary) {
            val resources = binding.root.resources
            val count = playlist.songCount
            binding.tvName.text = playlist.name
            // "24 songs · 1 h 32 min", counting downloaded songs only (spec).
            binding.tvDetails.text = joinWithDots(
                listOf(
                    resources.getQuantityString(R.plurals.song_count, count, count),
                    formatListLength(resources, playlist.durationsMs)
                )
            )
            loadArtwork(binding.ivCover, playlist.coverPath)
            binding.root.setOnClickListener { onClick(playlist) }
        }
    }
}

private object PlaylistDiff : DiffUtil.ItemCallback<PlaylistSummary>() {
    override fun areItemsTheSame(oldItem: PlaylistSummary, newItem: PlaylistSummary) =
        oldItem.playlistId == newItem.playlistId

    override fun areContentsTheSame(oldItem: PlaylistSummary, newItem: PlaylistSummary) =
        oldItem == newItem
}
