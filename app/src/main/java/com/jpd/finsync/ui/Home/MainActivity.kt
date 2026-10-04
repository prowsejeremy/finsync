package com.jpd.finsync.ui

import android.content.ComponentName
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.common.util.concurrent.ListenableFuture
import com.jpd.finsync.R
import com.jpd.finsync.databinding.ActivityMainBinding
import com.jpd.finsync.playback.PlaybackService
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

private const val TAG = "MainActivity"

/** Hosts every screen after login. Navigation swaps the screens; see res/navigation/nav_graph.xml. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val viewModel: MainViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var hasQueue = false
    private var onPlayerScreen = false
    // The art the mini-player shows, so it's only reloaded when the track's art changes.
    private var miniArtworkPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!viewModel.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        observeViewModel()
        setUpMiniPlayer()
        observePlaybackMessages()
        viewModel.checkServerConnection() // Initial check; will also be triggered by network callback and ServerBottomSheet.
    }

    // One controller per visible activity: built here, released in onStop.
    override fun onStart() {
        super.onStart()
        connectToPlayback()
    }

    override fun onStop() {
        disconnectFromPlayback()
        super.onStop()
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

    private fun observeViewModel() {
        viewModel.config.observe(this) { config ->
            if (config == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun setUpMiniPlayer() {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = navHost.navController
        navController.addOnDestinationChangedListener { _, destination, _ ->
            onPlayerScreen = destination.id == R.id.playerFragment
            updateMiniPlayerVisibility()
        }

        val mini = binding.miniPlayer
        mini.root.setOnClickListener {
            navController.navigateUnlessShowing(R.id.playerFragment, R.id.action_global_player)
        }
        mini.btnMiniPlayPause.setOnClickListener { playbackViewModel.togglePlayPause() }
        mini.btnMiniNext.setOnClickListener { playbackViewModel.next() }

        playbackViewModel.state.observe(this) { renderMiniPlayer(it) }
        playbackViewModel.position.observe(this) { position ->
            mini.miniProgress.progress =
                progressPermille(position.positionMs, position.durationMs)
        }
    }

    private fun renderMiniPlayer(state: PlaybackUiState) {
        hasQueue = state.hasQueue
        updateMiniPlayerVisibility()
        val mini = binding.miniPlayer
        mini.tvMiniTitle.text = state.title
        mini.tvMiniArtist.text = state.artist
        val playIcon = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playLabel = if (state.isPlaying) R.string.cd_pause else R.string.cd_play
        mini.ivMiniPlayPause.setImageResource(playIcon)
        mini.btnMiniPlayPause.contentDescription = getString(playLabel)
        if (state.artworkPath != miniArtworkPath) {
            miniArtworkPath = state.artworkPath
            loadArtwork(mini.ivMiniArt, state.artworkPath)
        }
    }

    // Shown on every screen, Settings included, whenever the queue isn't empty, except the Player.
    private fun updateMiniPlayerVisibility() {
        val visible = hasQueue && !onPlayerScreen
        binding.miniPlayerContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun observePlaybackMessages() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playbackViewModel.messages.collect { messageId ->
                    Toast.makeText(this@MainActivity, messageId, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun connectToPlayback() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            // Ignore a connection that finished after onStop released it.
            if (controllerFuture !== future || future.isCancelled) return@addListener
            try {
                playbackViewModel.attach(future.get())
            } catch (e: ExecutionException) {
                Log.e(TAG, "Couldn't connect to the playback service", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun disconnectFromPlayback() {
        playbackViewModel.detach()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }
}
