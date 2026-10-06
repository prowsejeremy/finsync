package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.jpd.hz.databinding.FragmentAllSongsBinding
import com.jpd.hz.library.GroupDetail

/** An artist's or genre's All songs, laid out like album detail. */
class AllSongsFragment : Fragment() {

    private var _binding: FragmentAllSongsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GroupViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private lateinit var adapter: SongsAdapter
    // The list in album order; playback starts from an index into this.
    private var songIds: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }

        adapter = SongsAdapter(SongListStyle.ALL_SONGS) { song -> playFrom(song.itemId) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnPlay.setOnClickListener {
            playbackViewModel.playTracks(songIds, 0, shuffle = false)
        }
        binding.btnShuffle.setOnClickListener {
            playbackViewModel.playTracks(songIds, 0, shuffle = true)
        }

        viewModel.group.observe(viewLifecycleOwner) { render(it) }
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

    private fun render(group: GroupDetail?) {
        binding.content.visibility = if (group != null) View.VISIBLE else View.GONE
        binding.tvNotDownloaded.visibility = if (group == null) View.VISIBLE else View.GONE
        songIds = group?.songs?.map { it.itemId } ?: emptyList()
        if (group == null) return

        val covers = binding.covers
        bindStackedCovers(
            covers.ivCoverFront, covers.ivCoverMiddle, covers.ivCoverBack, stackedCovers(group)
        )
        binding.tvGroupName.text = group.name
        binding.tvSummary.text = songsSummary(resources, group.songs)
        adapter.submitList(group.songs)
    }
}
