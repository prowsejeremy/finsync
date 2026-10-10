package com.jpd.hz.ui

import android.content.ComponentName
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.common.util.concurrent.ListenableFuture
import com.jpd.hz.R
import com.jpd.hz.appearance.applyAccentOverlay
import com.jpd.hz.databinding.ActivityMainBinding
import com.jpd.hz.playback.PlaybackService
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

private const val TAG = "MainActivity"

/**
 * Hosts every screen, with or without a Jellyfin sign-in (D10). Navigation swaps the screens; see
 * res/navigation/nav_graph.xml.
 */
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
        // Before anything is inflated, so every screen and sheet uses the saved accent. A new
        // accent recreates the activity (AppearanceFragment), which comes back through here.
        applyAccentOverlay()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpMiniPlayer()
        observePlaybackMessages()
        // Also creates the view model, whose launch settle and scan run whichever screen opens.
        viewModel.checkConnections()
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
            runOnUiThread { viewModel.checkConnections() }
        }
        override fun onLost(network: Network) {
            runOnUiThread { viewModel.checkConnections() }
        }
    }

    override fun onResume() {
        super.onResume()
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onPause() {
        super.onPause()
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.unregisterNetworkCallback(networkCallback)
    }

    private fun setUpMiniPlayer() {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = navHost.navController
        navController.addOnDestinationChangedListener { _, destination, _ ->
            onPlayerScreen = destination.id == R.id.playerFragment
            updateMiniPlayerVisibility()
        }

        val mini = binding.miniPlayer
        val openPlayer = View.OnClickListener {
            navController.navigateUnlessShowing(R.id.playerFragment, R.id.action_global_player)
        }
        // The swipe layout takes every touch the buttons don't (it must, to see a drag), so a
        // tap opens the Player through it. The card keeps the listener for TalkBack's double tap.
        mini.root.setOnClickListener(openPlayer)
        mini.miniSwipe.setOnClickListener(openPlayer)
        mini.miniSwipe.listener =
            TrackSwipe(mini.miniSwipe, mini.miniTrackInfo, playbackViewModel)
        addMiniPlayerActions()
        mini.btnMiniPlayPause.setOnClickListener { playbackViewModel.togglePlayPause() }
        // On a book this is now the next chapter, not +30 s (the user's choice, 3b refinements).
        mini.btnMiniNext.setOnClickListener { playbackViewModel.nextTrackOrChapter() }

        playbackViewModel.state.observe(this) { renderMiniPlayer(it) }
        playbackViewModel.position.observe(this) { position ->
            mini.miniProgress.progress =
                progressPermille(position.positionMs, position.durationMs)
        }
    }

    // TalkBack can't swipe the card, so it gets the same two moves as actions (spec "TalkBack").
    private fun addMiniPlayerActions() {
        val card = binding.miniPlayer.root
        ViewCompat.addAccessibilityAction(card, getString(R.string.cd_next)) { _, _ ->
            playbackViewModel.nextTrackOrChapter()
            true
        }
        ViewCompat.addAccessibilityAction(card, getString(R.string.cd_previous)) { _, _ ->
            playbackViewModel.previousTrackOrChapter()
            true
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
        val nextLabel = if (state.book != null) R.string.cd_next_chapter else R.string.cd_next
        mini.btnMiniNext.contentDescription = getString(nextLabel)
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
