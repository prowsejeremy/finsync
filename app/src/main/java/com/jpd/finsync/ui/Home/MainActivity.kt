package com.jpd.finsync.ui

import android.content.Intent
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.Network
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jpd.finsync.R
import com.jpd.finsync.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!viewModel.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.navBar, NavBarFragment.newInstance(NavBarFragment.Tab.HOME))
                .commit()
        }

        setupViews()
        observeViewModel()
        viewModel.checkServerConnection() // Initial check; will also be triggered by network callback and ServerBottomSheet.
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { viewModel.checkServerConnection() }
        }
        override fun onLost(network: Network) {
            runOnUiThread { viewModel.checkServerConnection() }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshConfig()
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onPause() {
        super.onPause()
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.unregisterNetworkCallback(networkCallback)
    }

    private fun setupViews() {
        binding.btnServerPill.setOnClickListener {
            ServerBottomSheet().show(supportFragmentManager, ServerBottomSheet.TAG)
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(0, 0)
        }

        binding.btnSyncNow.setOnClickListener {
            if (viewModel.uiState.value?.syncState?.isRunning == true) {
                viewModel.stopSync()
            } else {
                viewModel.startSync()
            }
        }

    }

    private fun observeViewModel() {
        viewModel.config.observe(this) { config ->
            if (config == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return@observe
            }
            binding.tvServerName.text = config.serverName
        }

        viewModel.uiState.observe(this) { render(it) }
    }

    private fun render(state: MainViewModel.UiState) {

        // Handle offline mode first, which takes precedence over any sync state.
        if (!state.serverConnected) {
            viewModel.stopSync()
            binding.tvSyncState.text = "Lost connection to server"
            binding.tvTrackCount.text = "OFFLINE"
            binding.tvTrackCount.textSize = 50.0f
            binding.tvTrackTotal.text = "Please reconnect to sync."
            binding.tvSyncState.setTextColor(ContextCompat.getColor(this, R.color.muted))
            binding.btnSyncNow.isEnabled = false
            binding.btnSyncNow.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_2))
            binding.btnSyncNow.setTextColor(ContextCompat.getColor(this, R.color.muted))
            return
        }

        // Reset to default state for online mode; will be overridden if syncState is non-null.
        binding.tvTrackCount.textSize = 60.0f
        binding.btnSyncNow.isEnabled = true
        binding.btnSyncNow.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        binding.btnSyncNow.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

        // If syncState is null, it means we haven't started syncing yet, so show the default "not synced" state.
        val syncState = state.syncState ?: return
        val (syncedTracks, totalTracks) = state.trackStats
        val isRunning   = syncState.isRunning
        val isIdle      = !isRunning && !syncState.wasStopped && syncState.errorMessage == null
        val hasTracks   = totalTracks > 0
        val fullySynced = syncState.syncComplete || (isIdle && hasTracks && syncedTracks >= totalTracks)

        // Syncing, stopped, error, fully synced, or partially synced states all fall under the "not idle" category and are rendered similarly (with different text/colors).
        binding.syncBlobView.isSyncing = isRunning
        binding.syncBlobView.progress  = when {
            isRunning && syncState.totalItems > 0 -> syncState.downloadedItems.toFloat() / syncState.totalItems
            fullySynced                           -> 1f
            else                                  -> 0f
        }

        binding.tvSyncState.text = when {
            isRunning                       -> "Syncing..."
            syncState.wasStopped            -> "Sync stopped"
            syncState.errorMessage != null  -> "Sync failed"
            fullySynced                     -> "Synced"
            else                            -> "Not synced yet"
        }
        binding.tvSyncState.setTextColor(
            if (fullySynced) ContextCompat.getColor(this, R.color.accent_green)
            else ContextCompat.getColor(this, R.color.muted)
        )

        binding.tvTrackCount.text = when {
            isRunning            -> syncState.downloadedItems.toString()
            syncState.wasStopped -> syncState.downloadedItems.toString()
            !hasTracks           -> "—"
            else                 -> syncedTracks.toString()
        }

        binding.tvTrackTotal.text = if (hasTracks) "of $totalTracks tracks synced" else ""

        binding.btnSyncNow.text = if (isRunning) {
            getString(R.string.btn_stop_sync)
        } else {
            getString(R.string.btn_sync_now)
        }

        syncState.errorMessage?.let { err ->
            Snackbar.make(binding.root, "Sync error: $err", Snackbar.LENGTH_LONG).show()
        }
    }
}
