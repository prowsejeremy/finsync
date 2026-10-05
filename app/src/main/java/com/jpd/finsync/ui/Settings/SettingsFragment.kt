package com.jpd.finsync.ui

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentSettingsBinding
import com.jpd.finsync.home.HomeLayoutStore

/**
 * Settings' top level (spec "Settings (top level)"): the server pill, then a row for each
 * sub-menu. The sync cards live on the Sync screen ([SyncSettingsFragment]).
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.setText(R.string.header_settings)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }

        bindServer()
        bindSyncRow()
        bindHomeScreenRow()
    }

    override fun onResume() {
        super.onResume()
        // The Home screen settings may have changed what Home shows.
        renderHomeScreenSummary()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Server ────────────────────────────────────────────────────────────────

    private fun bindServer() {
        mainViewModel.config.observe(viewLifecycleOwner) { config ->
            binding.tvServerName.text = config?.serverName.orEmpty()
        }
        binding.cardServer.setOnClickListener {
            ServerBottomSheet().show(childFragmentManager, ServerBottomSheet.TAG)
        }
    }

    private fun renderServerStatus(connected: Boolean) {
        binding.serverStatusDot.setBackgroundResource(
            if (connected) R.drawable.circle_status_good else R.drawable.circle_accent_muted
        )
        binding.tvServerStatus.setText(
            if (connected) R.string.server_connected else R.string.server_offline
        )
    }

    // ── Sync row ──────────────────────────────────────────────────────────────

    private fun bindSyncRow() {
        binding.rowSync.ivRowIcon.setImageResource(R.drawable.ic_sync)
        binding.rowSync.tvRowTitle.setText(R.string.settings_sync_title)
        // One observer for the Connected dot and the row, which updates live while Settings is
        // open (spec "Sync row summary").
        mainViewModel.uiState.observe(viewLifecycleOwner) { state ->
            renderServerStatus(state.serverConnected)
            renderSyncRow(SyncDisplay.from(state))
        }
        binding.rowSync.root.setOnClickListener {
            navigateSafely(R.id.settingsFragment, R.id.action_settings_to_sync)
        }
    }

    // "Synced · 1,400 of 1,400 songs": the label in its status colour, the rest in the layout's
    // muted.
    private fun renderSyncRow(display: SyncDisplay) {
        val summary = SyncRowSummary.from(display)
        val label = getString(summary.status.labelRes)
        val text = SpannableString(joinWithDots(listOf(label, resources.syncRowDetail(summary))))
        text.setSpan(
            ForegroundColorSpan(color(summary.status.labelColorRes)),
            0,
            label.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.rowSync.tvRowSummary.text = text
    }

    // ── Home screen row ───────────────────────────────────────────────────────

    private fun bindHomeScreenRow() {
        binding.rowHomeScreen.ivRowIcon.setImageResource(R.drawable.ic_home)
        binding.rowHomeScreen.tvRowTitle.setText(R.string.settings_home_screen_title)
        binding.rowHomeScreen.root.setOnClickListener {
            navigateSafely(R.id.settingsFragment, R.id.action_settings_to_home_screen)
        }
    }

    // "6 of 6 categories shown" (spec "Settings card").
    private fun renderHomeScreenSummary() {
        val layout = HomeLayoutStore(requireContext()).load()
        binding.rowHomeScreen.tvRowSummary.text = getString(
            R.string.settings_home_screen_summary,
            layout.visible.size,
            layout.order.size
        )
    }

    private fun color(@ColorRes colorRes: Int) = ContextCompat.getColor(requireContext(), colorRes)
}
