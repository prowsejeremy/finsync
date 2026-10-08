package com.jpd.hz.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentSyncSettingsBinding

/**
 * Adapters → Jellyfin (spec "Settings → Adapters"): the server pill, the Sync card and the sync
 * settings; signed out, a Sign in card instead. The choice screens' Back and Cancel / Apply
 * return here.
 */
class SyncSettingsFragment : Fragment() {

    private var _binding: FragmentSyncSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by navGraphViewModels(R.id.settings_graph)
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSyncSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.text = mainViewModel.jellyfin.name
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }

        bindSignIn()
        bindSyncCard()
        bindSyncPreferences()
    }

    override fun onResume() {
        super.onResume()
        // The choice screens can change the selections and the schedule. The Sync card's counts
        // depend on the selections, so recount them (from the stored catalogue).
        mainViewModel.refreshSyncCounts()
        binding.tvAutoSync.text = autoSyncLabel(viewModel.getAutoSyncInterval())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Sign-in and server ────────────────────────────────────────────────────

    // MainActivity reads the sign-in again on every resume, so this runs on coming back from
    // LoginActivity, and at once on signing out (T4).
    private fun bindSignIn() {
        mainViewModel.config.observe(viewLifecycleOwner) { config ->
            binding.cardSignIn.isVisible = config == null
            binding.signedInContent.isVisible = config != null
            config ?: return@observe
            binding.tvServerName.text = config.serverName
            // Settings → Library can move it, so it's read again each time.
            val folder = mainViewModel.jellyfin.folder()
            binding.tvSyncTarget.text =
                folder?.let { getString(R.string.sync_target, it) }.orEmpty()
            viewModel.loadAlbumsFor(config)
        }
        binding.btnSignIn.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
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

    // ── Sync card ─────────────────────────────────────────────────────────────

    private fun bindSyncCard() {
        // One observer for the Connected dot and the card.
        mainViewModel.uiState.observe(viewLifecycleOwner) { state ->
            renderServerStatus(state.serverConnected)
            renderSyncCard(SyncDisplay.from(state))
        }
        // The card itself has no listener since Sync Status went, so it has no ripple either.
        binding.btnSyncCard.setOnClickListener { mainViewModel.toggleSync() }
    }

    private fun renderSyncCard(display: SyncDisplay) {
        val isOffline = display.status == SyncDisplay.Status.OFFLINE
        binding.tvSyncCardStatus.setText(display.status.labelRes)
        binding.tvSyncCardStatus.setTextColor(color(display.status.labelColorRes))

        // The words come from string resources; the line is put together here (spec "Sync card
        // detail line").
        val detail = when (display.status) {
            SyncDisplay.Status.OFFLINE -> getString(R.string.sync_detail_offline)
            SyncDisplay.Status.SYNCING -> resources.syncRunLine(display.runDone, display.runTotal)
            SyncDisplay.Status.FAILED ->
                getString(R.string.sync_error, display.errorMessage.orEmpty())
            SyncDisplay.Status.INCOMPLETE -> resources.syncIncompleteDetail(display.failedItems)
            // Stopped, synced or not synced: the counts, or nothing when nothing is selected.
            else -> resources.syncCountsLine(display.counts)
        }
        // "2 files couldn't be tagged." follows it, without making the sync incomplete (T2).
        val line = resources.withUntaggedDetail(detail, display.untaggedFiles)
        binding.tvSyncCardDetail.text = line
        binding.tvSyncCardDetail.isVisible = line != null

        if (display.status == SyncDisplay.Status.SYNCING) {
            binding.syncCardProgress.showSyncProgress(display.progress)
        } else {
            binding.syncCardProgress.hideSyncProgress()
        }

        binding.btnSyncCard.setText(display.buttonLabelRes)
        binding.btnSyncCard.isEnabled = !isOffline
        binding.btnSyncCard.setTextColor(color(if (isOffline) R.color.muted else R.color.text_primary))
    }

    // ── Sync preferences ──────────────────────────────────────────────────────

    private fun bindSyncPreferences() {
        viewModel.albums.observe(viewLifecycleOwner) { albums ->
            val selectedIds = viewModel.getSelectedAlbumIds()
            val total = albums.size
            val isAll = selectedIds.contains("all") || selectedIds.isEmpty()
            val selectedCount = if (isAll) total else minOf(selectedIds.size, total)
            // Moved to a string (spec "Sync (new screen)").
            binding.tvAlbumsSummary.text =
                getString(R.string.settings_albums_summary, selectedCount, total)
        }
        // From the catalogue; an empty selection means none (3b).
        viewModel.playlistChoices.observe(viewLifecycleOwner) { playlists ->
            val selectedIds = viewModel.getSelectedPlaylistIds()
            val selected = playlists.count { it.playlistId in selectedIds }
            binding.tvPlaylistsSummary.text =
                getString(R.string.settings_playlists_summary, selected, playlists.size)
        }
        // The from-ID is this screen now (decision 2).
        binding.cardAlbums.setOnClickListener {
            navigateSafely(R.id.syncSettingsFragment, R.id.action_settings_to_album_selection)
        }
        binding.cardPlaylists.setOnClickListener {
            navigateSafely(R.id.syncSettingsFragment, R.id.action_settings_to_playlist_selection)
        }
        viewModel.bookChoices.observe(viewLifecycleOwner) { books ->
            val selectedIds = viewModel.getSelectedBookIds()
            val selected = books.count { it.bookId in selectedIds }
            binding.tvBooksSummary.text =
                getString(R.string.settings_books_summary, selected, books.size)
        }
        binding.cardBooks.setOnClickListener {
            navigateSafely(R.id.syncSettingsFragment, R.id.action_settings_to_book_selection)
        }
        binding.cardAutoSync.setOnClickListener {
            navigateSafely(R.id.syncSettingsFragment, R.id.action_settings_to_auto_sync)
        }
    }

    // The same four intervals as before, now from strings: "Every 6 hours", or "Disabled".
    private fun autoSyncLabel(interval: String): String = when (interval) {
        "1", "6", "12", "24" -> {
            val hours = interval.toInt()
            resources.getQuantityString(R.plurals.settings_auto_sync_every, hours, hours)
        }
        else -> getString(R.string.settings_auto_sync_disabled)
    }

    private fun color(@ColorRes colorRes: Int) = ContextCompat.getColor(requireContext(), colorRes)
}
