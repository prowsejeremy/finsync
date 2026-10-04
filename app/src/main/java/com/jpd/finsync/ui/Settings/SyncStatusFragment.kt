package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentSyncStatusBinding

private const val COUNT_TEXT_SIZE_SP = 60f
private const val OFFLINE_COUNT_TEXT_SIZE_SP = 50f

class SyncStatusFragment : Fragment() {

    private var _binding: FragmentSyncStatusBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSyncStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.setText(R.string.header_sync_status)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnSyncNow.setOnClickListener { viewModel.toggleSync() }
        viewModel.uiState.observe(viewLifecycleOwner) { render(SyncDisplay.from(it)) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(display: SyncDisplay) {
        binding.tvSyncState.setText(display.status.labelRes)
        binding.tvSyncState.setTextColor(color(display.status.labelColorRes))
        // Set before the offline branch returns, so going offline mid-sync stops the blob and
        // resets the button label.
        binding.syncBlobView.isSyncing = display.status == SyncDisplay.Status.SYNCING
        binding.syncBlobView.progress = display.progress ?: 0f
        binding.btnSyncNow.setText(display.buttonLabelRes)

        if (display.status == SyncDisplay.Status.OFFLINE) {
            binding.tvTrackCount.setText(R.string.sync_count_offline)
            binding.tvTrackCount.textSize = OFFLINE_COUNT_TEXT_SIZE_SP
            binding.tvTrackTotal.setText(R.string.sync_detail_offline)
            binding.btnSyncNow.isEnabled = false
            binding.btnSyncNow.setBackgroundColor(color(R.color.surface_2))
            binding.btnSyncNow.setTextColor(color(R.color.muted))
            return
        }

        binding.tvTrackCount.textSize = COUNT_TEXT_SIZE_SP
        binding.btnSyncNow.isEnabled = true
        binding.btnSyncNow.setBackgroundColor(color(android.R.color.transparent))
        binding.btnSyncNow.setTextColor(color(R.color.text_primary))

        binding.tvTrackCount.text =
            display.trackCount?.toString() ?: getString(R.string.sync_count_none)
        binding.tvTrackTotal.text = if (display.totalTracks > 0) {
            getString(R.string.sync_track_total, display.totalTracks)
        } else {
            ""
        }

        display.errorMessage?.let { error ->
            val message = getString(R.string.sync_error, error)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
        if (display.failedItems > 0) {
            val message = resources.syncIncompleteMessage(display.failedItems)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun color(@ColorRes colorRes: Int) = ContextCompat.getColor(requireContext(), colorRes)
}
