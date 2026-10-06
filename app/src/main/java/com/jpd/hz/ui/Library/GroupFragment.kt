package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentGroupBinding
import com.jpd.hz.library.GroupDetail

/** Navigation arguments for the artist and genre pages and their All songs (strings, required). */
const val ARG_GROUP_TYPE = "groupType"
const val ARG_GROUP_ID = "groupId"
const val GROUP_TYPE_ARTIST = "artist"
const val GROUP_TYPE_GENRE = "genre"

class GroupFragment : Fragment() {

    private var _binding: FragmentGroupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GroupViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private lateinit var adapter: GroupAdapter
    // The All songs list in album order; Play and Shuffle play it.
    private var songIds: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }

        // Genre pages mix artists, so their album rows name each album's artist.
        adapter = GroupAdapter(
            showAlbumArtist = !viewModel.isArtist,
            onAllSongsClick = ::openAllSongs,
            onAlbumClick = { album -> openAlbum(album.albumId) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnPlay.setOnClickListener {
            playbackViewModel.playTracks(songIds, 0, shuffle = false)
        }
        binding.btnShuffle.setOnClickListener {
            playbackViewModel.playTracks(songIds, 0, shuffle = true)
        }

        viewModel.group.observe(viewLifecycleOwner) { render(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(group: GroupDetail?) {
        binding.content.visibility = if (group != null) View.VISIBLE else View.GONE
        binding.tvNotDownloaded.visibility = if (group == null) View.VISIBLE else View.GONE
        songIds = group?.songs?.map { it.itemId } ?: emptyList()
        if (group == null) return

        val albumCount = group.albums.size
        val songCount = group.songs.size
        binding.tvName.text = group.name
        binding.tvCounts.text = joinWithDots(
            listOf(
                resources.getQuantityString(R.plurals.album_count, albumCount, albumCount),
                resources.getQuantityString(R.plurals.song_count, songCount, songCount)
            )
        )
        binding.avatar.visibility = if (viewModel.isArtist) View.VISIBLE else View.GONE
        if (viewModel.isArtist) {
            bindAvatar(
                binding.ivPhoto,
                binding.tvInitials,
                binding.ivPersonIcon,
                group.name,
                group.photoPath
            )
        }
        adapter.submitList(groupRows(group))
    }

    private fun openAllSongs() = navigateSafely(
        R.id.groupFragment,
        R.id.action_group_to_all_songs,
        bundleOf(ARG_GROUP_TYPE to viewModel.groupType, ARG_GROUP_ID to viewModel.groupId)
    )

    private fun openAlbum(albumId: String) = navigateSafely(
        R.id.groupFragment,
        R.id.action_group_to_album,
        bundleOf(ARG_ALBUM_ID to albumId)
    )
}
