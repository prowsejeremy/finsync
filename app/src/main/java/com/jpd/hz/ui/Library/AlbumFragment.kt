package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentAlbumBinding
import com.jpd.hz.library.AlbumDetail

/** Navigation argument naming the album to show (string, required). */
const val ARG_ALBUM_ID = "albumId"

class AlbumFragment : Fragment() {

    private var _binding: FragmentAlbumBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AlbumViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private lateinit var adapter: AlbumTracksAdapter
    // The album's downloaded tracks in album order; playback starts from an index into this.
    private var trackIds: List<String> = emptyList()

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

        adapter = AlbumTracksAdapter { row ->
            playbackViewModel.playTracks(trackIds, row.queueIndex, shuffle = false)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnPlay.setOnClickListener {
            playbackViewModel.playTracks(trackIds, 0, shuffle = false)
        }
        binding.btnShuffle.setOnClickListener {
            playbackViewModel.playTracks(trackIds, 0, shuffle = true)
        }

        viewModel.album.observe(viewLifecycleOwner) { render(it) }
        playbackViewModel.state.observe(viewLifecycleOwner) { adapter.setPlayingItemId(it.mediaId) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(album: AlbumDetail?) {
        binding.content.visibility = if (album != null) View.VISIBLE else View.GONE
        binding.tvNotDownloaded.visibility = if (album == null) View.VISIBLE else View.GONE
        trackIds = album?.tracks?.map { it.itemId } ?: emptyList()
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
