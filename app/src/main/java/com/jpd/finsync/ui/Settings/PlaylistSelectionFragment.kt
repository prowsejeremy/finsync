package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentAlbumSelectionBinding

/** Playlists to Sync: Albums to Sync's checklist over the server's audio playlists (3b). */
class PlaylistSelectionFragment : Fragment() {

    private var _binding: FragmentAlbumSelectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by navGraphViewModels(R.id.settings_graph)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.setText(R.string.header_settings)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.tvSelectionTitle.setText(R.string.selection_title_playlists)

        val choices = SyncChoicesController(
            binding,
            onSave = viewModel::setSelectedPlaylistIds,
            onClose = { findNavController().popBackStack() }
        )
        viewModel.refreshCatalogueOnce()
        viewModel.playlistChoices.observe(viewLifecycleOwner) { playlists ->
            val rows = playlists.map { playlist ->
                SyncChoice(
                    id = playlist.playlistId,
                    title = playlist.name,
                    subtitle = resources.getQuantityString(
                        R.plurals.song_count, playlist.songCount, playlist.songCount
                    )
                )
            }
            choices.submit(rows, viewModel.getSelectedPlaylistIds())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
