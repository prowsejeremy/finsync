package com.jpd.finsync.ui

import android.content.ComponentName
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jpd.finsync.databinding.ActivityMainBinding
import com.jpd.finsync.playback.PlaybackService
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

private const val TAG = "MainActivity"

/** Hosts every screen after login. Navigation swaps the screens; see res/navigation/nav_graph.xml. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null

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
