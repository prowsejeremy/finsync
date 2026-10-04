package com.jpd.finsync.ui

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.jpd.finsync.databinding.ActivityMainBinding

/** Hosts every screen after login. Navigation swaps the screens; see res/navigation/nav_graph.xml. */
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

    private fun observeViewModel() {
        viewModel.config.observe(this) { config ->
            if (config == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }
}
