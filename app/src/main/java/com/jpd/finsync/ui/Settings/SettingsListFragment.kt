package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentSettingsListBinding

class SettingsListFragment : Fragment() {

    private var _binding: FragmentSettingsListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.albums.observe(viewLifecycleOwner) { albums ->
            val selectedIds = viewModel.getSelectedAlbumIds()
            val total = albums.size
            val isAll = selectedIds.contains("all") || selectedIds.isEmpty()
            val selectedCount = if (isAll) total else minOf(selectedIds.size, total)
            binding.tvAlbumsSummary.text = "$selectedCount of $total albums selected"
        }

        viewModel.syncDir.observe(viewLifecycleOwner) { path ->
            binding.tvSyncDir.text = path ?: "—"
        }

        binding.tvAutoSync.text = autoSyncLabel(viewModel.getAutoSyncInterval())

        binding.cardAlbums.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, AlbumSelectionFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.cardSyncDir.setOnClickListener {
            (activity as? SettingsActivity)?.openFolderPicker()
        }

        binding.cardAutoSync.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, AutoSyncFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh auto-sync label when returning from AutoSyncFragment
        if (_binding != null) {
            binding.tvAutoSync.text = autoSyncLabel(viewModel.getAutoSyncInterval())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun autoSyncLabel(interval: String) = when (interval) {
        "1"  -> "Every 1 hour"
        "6"  -> "Every 6 hours"
        "12" -> "Every 12 hours"
        "24" -> "Every 24 hours"
        else -> "Disabled"
    }
}
