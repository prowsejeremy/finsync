package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.jpd.finsync.databinding.FragmentPlaylistBinding
import com.jpd.finsync.library.PlaylistDetail
import com.jpd.finsync.library.SongRow

/** Navigation argument naming the playlist to show (string, required). */
const val ARG_PLAYLIST_ID = "playlistId"

/** A playlist's downloaded songs in server order; read-only (spec "Playlist"). */
class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlaylistViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private lateinit var adapter: SongsAdapter
    // The rows as shown; playback starts from an index into them.
    private var songs: List<SongRow> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }

        adapter = SongsAdapter(SongListStyle.SONGS) { song -> playFrom(song) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnPlay.setOnClickListener {
            playbackViewModel.playTracks(entryIds(), 0, shuffle = false)
        }
        binding.btnShuffle.setOnClickListener {
            playbackViewModel.playTracks(entryIds(), 0, shuffle = true)
        }

        viewModel.playlist.observe(viewLifecycleOwner) { render(it) }
        playbackViewModel.state.observe(viewLifecycleOwner) { adapter.setPlayingItemId(it.mediaId) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(playlist: PlaylistDetail?) {
        binding.content.visibility = if (playlist != null) View.VISIBLE else View.GONE
        binding.tvNotDownloaded.visibility = if (playlist == null) View.VISIBLE else View.GONE
        songs = playlist?.songs ?: emptyList()
        if (playlist == null) return
        binding.tvName.text = playlist.name
        binding.tvDetails.text = songsSummary(resources, playlist.songs)
        loadArtwork(binding.ivCover, playlist.coverPath)
        adapter.submitList(playlist.songs)
    }

    private fun entryIds(): List<String> = songs.map { it.itemId }

    // A song can appear twice, so the tapped row is found by identity first. Until the adapter
    // shows the latest list, the first row with that ID stands in.
    private fun playFrom(song: SongRow) {
        val byIdentity = songs.indexOfFirst { it === song }
        val index = if (byIdentity >= 0) {
            byIdentity
        } else {
            songs.indexOfFirst { it.itemId == song.itemId }
        }
        if (index >= 0) playbackViewModel.playTracks(entryIds(), index, shuffle = false)
    }
}
