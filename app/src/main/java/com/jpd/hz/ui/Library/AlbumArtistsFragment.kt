package com.jpd.hz.ui

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
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentAlbumArtistsBinding
import com.jpd.hz.databinding.ItemArtistBinding
import com.jpd.hz.library.ArtistSummary

class AlbumArtistsFragment : Fragment() {

    private var _binding: FragmentAlbumArtistsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AlbumArtistsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumArtistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.header.tvTitle.setText(R.string.header_album_artists)
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }

        val adapter = ArtistsAdapter { artist -> openArtist(artist.artistId) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewModel.artists.observe(viewLifecycleOwner) { artists ->
            adapter.submitList(artists)
            binding.tvEmpty.visibility = if (artists.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openArtist(artistId: String) = navigateSafely(
        R.id.albumArtistsFragment,
        R.id.action_album_artists_to_group,
        bundleOf(ARG_GROUP_TYPE to GROUP_TYPE_ARTIST, ARG_GROUP_ID to artistId)
    )
}

private class ArtistsAdapter(
    private val onClick: (ArtistSummary) -> Unit
) : ListAdapter<ArtistSummary, ArtistsAdapter.Holder>(ArtistDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemArtistBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemArtistBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(artist: ArtistSummary) {
            val count = artist.albumCount
            binding.tvName.text = artist.name
            binding.tvAlbumCount.text =
                binding.root.resources.getQuantityString(R.plurals.album_count, count, count)
            bindAvatar(
                binding.ivPhoto,
                binding.tvInitials,
                binding.ivPersonIcon,
                artist.name,
                artist.photoPath
            )
            binding.root.setOnClickListener { onClick(artist) }
        }
    }
}

private object ArtistDiff : DiffUtil.ItemCallback<ArtistSummary>() {
    override fun areItemsTheSame(oldItem: ArtistSummary, newItem: ArtistSummary) =
        oldItem.artistId == newItem.artistId

    override fun areContentsTheSame(oldItem: ArtistSummary, newItem: ArtistSummary) =
        oldItem == newItem
}
