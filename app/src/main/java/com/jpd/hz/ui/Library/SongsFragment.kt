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
import com.jpd.hz.databinding.FragmentSongsBinding

class SongsFragment : Fragment() {

    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SongsViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private lateinit var adapter: SongsAdapter
    // Every visible song, A–Z; playback starts from an index into this.
    private var songIds: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.header.tvTitle.setText(R.string.header_songs)
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }

        adapter = SongsAdapter(SongListStyle.SONGS) { song -> playFrom(song.itemId) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.btnShuffleAll.setOnClickListener {
            playbackViewModel.playTracks(songIds, 0, shuffle = true)
        }

        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            songIds = songs.map { it.itemId }
            adapter.submitList(songs)
            val empty = songs.isEmpty()
            binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            binding.btnShuffleAll.visibility = if (empty) View.GONE else View.VISIBLE
        }
        playbackViewModel.state.observe(viewLifecycleOwner) { adapter.setPlayingItemId(it.mediaId) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Plays the whole list from the tapped song, with shuffle off.
    private fun playFrom(itemId: String) {
        val index = songIds.indexOf(itemId)
        if (index >= 0) playbackViewModel.playTracks(songIds, index, shuffle = false)
    }
}
