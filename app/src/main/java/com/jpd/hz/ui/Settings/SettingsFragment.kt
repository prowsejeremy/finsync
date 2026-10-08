package com.jpd.hz.ui

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
import androidx.navigation.navGraphViewModels
import com.jpd.hz.R
import com.jpd.hz.appearance.nameRes
import com.jpd.hz.databinding.FragmentSettingsBinding
import com.jpd.hz.equaliser.EqualiserStore
import com.jpd.hz.home.HomeLayoutStore

/**
 * Settings' top level (spec "Settings (top level)"): the server pill, then a row for each
 * sub-menu. The sync cards live on the Sync screen ([SyncSettingsFragment]).
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by navGraphViewModels(R.id.settings_graph)
    private val mainViewModel: MainViewModel by activityViewModels()
    private val libraryViewModel: LibrarySettingsViewModel by
        navGraphViewModels(R.id.settings_graph)

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
        bindLibraryRow()
        bindSyncRow()
        bindAppearanceRow()
        bindHomeScreenRow()
        bindEqualiserRow()
    }

    override fun onResume() {
        super.onResume()
        // Appearance, the Home screen and the Equaliser may have changed since this was shown.
        renderAppearanceSummary()
        renderHomeScreenSummary()
        renderEqualiserSummary()
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

    // ── Library row ───────────────────────────────────────────────────────────

    private fun bindLibraryRow() {
        binding.rowLibrary.ivRowIcon.setImageResource(R.drawable.ic_folder)
        binding.rowLibrary.tvRowTitle.setText(R.string.settings_library_title)
        libraryViewModel.folder.observe(viewLifecycleOwner) { renderLibrarySummary() }
        libraryViewModel.songCount.observe(viewLifecycleOwner) { renderLibrarySummary() }
        binding.rowLibrary.root.setOnClickListener {
            navigateSafely(R.id.settingsFragment, R.id.action_settings_to_library)
        }
    }

    // "Media/hz · 5,300 songs" (spec "Settings → Library").
    private fun renderLibrarySummary() {
        val folder = libraryViewModel.folder.value ?: return
        val songs = libraryViewModel.songCount.value ?: 0
        binding.rowLibrary.tvRowSummary.text = resources.librarySummary(folder, songs)
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

    // ── Appearance row ────────────────────────────────────────────────────────

    private fun bindAppearanceRow() {
        binding.rowAppearance.ivRowIcon.setImageResource(R.drawable.ic_palette)
        binding.rowAppearance.tvRowTitle.setText(R.string.settings_appearance_title)
        binding.rowAppearance.root.setOnClickListener {
            navigateSafely(R.id.settingsFragment, R.id.action_settings_to_appearance)
        }
    }

    // "Dark · Green" (spec "Settings (top level)").
    private fun renderAppearanceSummary() {
        val mode = getString(viewModel.themeMode().nameRes)
        val accent = getString(viewModel.accent().nameRes)
        binding.rowAppearance.tvRowSummary.text = joinWithDots(listOf(mode, accent))
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

    // ── Equaliser row ─────────────────────────────────────────────────────────

    private fun bindEqualiserRow() {
        binding.rowEqualiser.ivRowIcon.setImageResource(R.drawable.ic_equaliser)
        binding.rowEqualiser.tvRowTitle.setText(R.string.equaliser_title)
        binding.rowEqualiser.root.setOnClickListener {
            navigateSafely(R.id.settingsFragment, R.id.action_settings_to_equaliser)
        }
    }

    // "Off", or "On · Bass boost" (spec "Settings").
    private fun renderEqualiserSummary() {
        val settings = EqualiserStore(requireContext()).load()
        binding.rowEqualiser.tvRowSummary.text = resources.equaliserSummary(settings)
    }

    private fun color(@ColorRes colorRes: Int) = ContextCompat.getColor(requireContext(), colorRes)
}
