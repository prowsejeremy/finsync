package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.jpd.hz.R
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.Platforms
import com.jpd.hz.adapter.choices.ChoiceRules
import com.jpd.hz.adapter.run.SyncStates
import com.jpd.hz.databinding.FragmentConnectionBinding
import com.jpd.hz.databinding.ItemChoiceRowBinding

/**
 * A platform's page (spec "Screens"), such as Adapters → Jellyfin: the server pill, the Sync
 * card, one "What to sync" row per kind of choice, Auto-sync and where it syncs; signed out, a
 * Sign in card instead. The choice screens' Back and Cancel / Apply return here.
 */
class ConnectionFragment : Fragment() {

    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ConnectionViewModel by navGraphViewModels(R.id.connection_graph)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.text = viewModel.platform.name
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.tvSignInDetail.setText(viewModel.platform.signInDetail)

        bindSignIn()
        bindSyncCard()
        bindChoiceRows()
        binding.cardAutoSync.setOnClickListener {
            navigateSafely(R.id.connectionFragment, R.id.action_connection_to_auto_sync)
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the sign-in screen, or from a choice screen that changed the choices.
        viewModel.refreshConnection()
        binding.tvAutoSync.text = autoSyncLabel(viewModel.autoSyncInterval())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Sign-in and server ────────────────────────────────────────────────────

    private fun bindSignIn() {
        viewModel.connection.observe(viewLifecycleOwner) { connection ->
            binding.cardSignIn.isVisible = connection == null
            binding.signedInContent.isVisible = connection != null
            connection ?: return@observe
            binding.tvServerName.text = connection.name
            // Settings → Library can move it, so it's read again each time.
            binding.tvSyncTarget.text =
                viewModel.folderName()?.let { getString(R.string.sync_target, it) }.orEmpty()
        }
        binding.btnSignIn.setOnClickListener { openSignIn() }
        binding.cardServer.setOnClickListener {
            ServerBottomSheet().show(childFragmentManager, ServerBottomSheet.TAG)
        }
    }

    private fun openSignIn() {
        startActivity(viewModel.platform.signInIntent(requireContext()))
    }

    private fun renderServerStatus(state: ConnectionUiState) {
        val connected = state.serverConnected && !state.signInRefused
        binding.serverStatusDot.setBackgroundResource(
            if (connected) R.drawable.circle_status_good else R.drawable.circle_accent_muted
        )
        binding.tvServerStatus.setText(
            when {
                state.signInRefused -> R.string.status_sign_in_again
                state.serverConnected -> R.string.server_connected
                else -> R.string.server_offline
            }
        )
    }

    // ── Sync card ─────────────────────────────────────────────────────────────

    private fun bindSyncCard() {
        // One observer for the Connected dot and the card.
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            renderServerStatus(state)
            renderSyncCard(SyncDisplay.from(state))
        }
        // A refused sign-in turns the card's button into Sign in (decision 4).
        binding.btnSyncCard.setOnClickListener {
            if (viewModel.uiState.value?.signInRefused == true) {
                openSignIn()
            } else {
                viewModel.toggleSync()
            }
        }
    }

    private fun renderSyncCard(display: SyncDisplay) {
        val isOffline = display.status == SyncDisplay.Status.OFFLINE
        binding.tvSyncCardStatus.setText(display.status.labelRes)
        binding.tvSyncCardStatus.setTextColor(color(display.status.labelColorRes))

        // The words come from string resources; the line is put together here (spec "Sync card
        // detail line").
        val detail = when (display.status) {
            SyncDisplay.Status.OFFLINE -> getString(R.string.sync_detail_offline)
            SyncDisplay.Status.WAITING -> getString(R.string.sync_detail_waiting, runningName())
            SyncDisplay.Status.SYNCING -> resources.syncRunLine(display.runDone, display.runTotal)
            SyncDisplay.Status.NOTHING_CHOSEN -> getString(R.string.sync_detail_nothing_chosen)
            SyncDisplay.Status.FAILED ->
                getString(R.string.sync_error, display.errorMessage.orEmpty())
            SyncDisplay.Status.INCOMPLETE -> resources.syncIncompleteDetail(display.failedItems)
            SyncDisplay.Status.SIGN_IN_AGAIN -> getString(
                R.string.sync_detail_sign_in_again,
                viewModel.connection.value?.name.orEmpty()
            )
            // Stopped, synced or not synced: the counts, or nothing when nothing is chosen.
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
        val buttonColor = if (isOffline) R.color.muted else R.color.text_primary
        binding.btnSyncCard.setTextColor(color(buttonColor))
    }

    // The connection whose run this one waits behind.
    private fun runningName(): String {
        val id = SyncStates.running.value ?: return ""
        return Platforms.connection(id)?.name.orEmpty()
    }

    // ── What to sync ──────────────────────────────────────────────────────────

    // One row per kind the platform offers, in its order: "Albums to Sync", "12 of 340 albums
    // selected". Rows read the stored catalogue, so they're shown signed in only.
    private fun bindChoiceRows() {
        binding.choiceRows.removeAllViews()
        viewModel.connection.observe(viewLifecycleOwner) { connection ->
            binding.choiceRows.removeAllViews()
            connection ?: return@observe
            viewModel.platform.choiceKinds.forEach { kind -> addChoiceRow(kind) }
        }
    }

    private fun addChoiceRow(kind: ChoiceKind) {
        val row = ItemChoiceRowBinding.inflate(layoutInflater, binding.choiceRows, false)
        row.tvChoiceTitle.setText(kind.rowTitleRes)
        viewModel.choices(kind).observe(viewLifecycleOwner) { groups ->
            val saved = viewModel.chosen(kind)
            val chosen = ChoiceRules.tickedOf(saved, groups.map { it.groupId }).size
            row.tvChoiceSummary.text = getString(kind.rowSummaryRes, chosen, groups.size)
        }
        row.root.setOnClickListener {
            navigateSafely(
                R.id.connectionFragment,
                R.id.action_connection_to_choices,
                ChoicesFragment.argsOf(kind)
            )
        }
        binding.choiceRows.addView(row.root)
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

    companion object {
        /** The page's arguments: which platform it shows. */
        fun argsOf(platform: String): Bundle = bundleOf(ARG_PLATFORM to platform)

        /** The page of [connection]'s platform. */
        fun argsOf(connection: Connection): Bundle = argsOf(connection.platform)
    }
}
