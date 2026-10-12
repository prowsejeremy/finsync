package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.jpd.hz.R
import com.jpd.hz.adapter.Platforms
import com.jpd.hz.appearance.nameRes
import com.jpd.hz.databinding.FragmentSettingsBinding
import com.jpd.hz.equaliser.EqualiserStore
import com.jpd.hz.home.HomeLayoutStore

/**
 * Settings' top level (spec "Settings → Adapters"): Library, Adapters, Appearance, Home screen and
 * Equaliser. The server pill and the sync cards live on each platform's page
 * ([ConnectionFragment]).
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by navGraphViewModels(R.id.settings_graph)
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

        bindLibraryRow()
        bindAdaptersRow()
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

    // ── Adapters row ──────────────────────────────────────────────────────────

    private fun bindAdaptersRow() {
        binding.rowAdapters.ivRowIcon.setImageResource(R.drawable.ic_sync)
        binding.rowAdapters.tvRowTitle.setText(R.string.settings_adapters_title)
        // "Jellyfin · Synced · 1,400 of 1,400 songs", live while Settings is open, as the Sync
        // row was (spec "Sync row summary"): the first platform signed in, else the first.
        val platform = Platforms.all.firstOrNull { it.connections().isNotEmpty() }
            ?: Platforms.all.firstOrNull()
        platform?.let {
            showAdapterSummary(it, before = { _ -> listOf(it.name) }) { summary ->
                binding.rowAdapters.tvRowSummary.text = summary
            }
        }
        binding.rowAdapters.root.setOnClickListener {
            navigateSafely(R.id.settingsFragment, R.id.action_settings_to_adapters)
        }
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
}
