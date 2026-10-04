package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentAlbumBinding
import com.jpd.finsync.library.AlbumDetail

/** Navigation argument naming the album to show (string, required). */
const val ARG_ALBUM_ID = "albumId"

class AlbumFragment : Fragment() {

    private var _binding: FragmentAlbumBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AlbumViewModel by viewModels()
    private lateinit var adapter: AlbumTracksAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }

        adapter = AlbumTracksAdapter { }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewModel.album.observe(viewLifecycleOwner) { render(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(album: AlbumDetail?) {
        binding.content.visibility = if (album != null) View.VISIBLE else View.GONE
        binding.tvNotDownloaded.visibility = if (album == null) View.VISIBLE else View.GONE
        if (album == null) return

        val count = album.tracks.size
        val minutes = albumLengthMinutes(album.tracks.map { it.durationMs })
        binding.tvAlbumTitle.text = album.name
        binding.tvAlbumArtist.text = album.albumArtist ?: getString(R.string.unknown_artist)
        binding.tvAlbumMeta.text = joinWithDots(
            listOf(
                album.year?.toString(),
                resources.getQuantityString(R.plurals.track_count, count, count),
                getString(R.string.album_length_minutes, minutes)
            )
        )
        loadArtwork(binding.ivAlbumArt, album.artworkPath)
        adapter.submitList(albumListRows(album.tracks, album.albumArtist))
    }
}
