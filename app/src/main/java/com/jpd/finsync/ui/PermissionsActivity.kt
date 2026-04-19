package com.jpd.finsync.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jpd.finsync.R
import com.jpd.finsync.databinding.ActivityPermissionsBinding

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateUi()
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUi()
    }

    private val notificationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (allPermissionsGranted()) {
            proceed()
            return
        }

        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermissions.setOnClickListener { requestAllPermissions() }
        binding.btnContinueAnyway.setOnClickListener { proceed() }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            updateUi()
            if (allPermissionsGranted()) proceed()
        }
    }

    private fun hasStorageWrite(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun allPermissionsGranted() = hasStorageWrite() && hasNotifications()

    private fun updateUi() {
        val storageOk = hasStorageWrite()
        val notifOk   = hasNotifications()

        binding.tvStorageStatus.text = if (storageOk) "✓ Storage access granted" else "✗ Storage access needed"
        binding.tvStorageStatus.setTextColor(
            getColor(if (storageOk) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )

        binding.tvNotifStatus.text = if (notifOk) "✓ Notifications granted" else "✗ Notifications needed (for sync progress)"
        binding.tvNotifStatus.setTextColor(
            getColor(if (notifOk) android.R.color.holo_green_dark else android.R.color.holo_orange_dark)
        )

        binding.btnGrantPermissions.text = when {
            !storageOk -> "Grant Storage Access"
            !notifOk   -> "Grant Notification Access"
            else       -> "All permissions granted"
        }
        binding.btnGrantPermissions.isEnabled = !allPermissionsGranted()
        binding.btnContinueAnyway.isEnabled   = !allPermissionsGranted()
    }

    private fun requestAllPermissions() {
        if (!hasStorageWrite()) {
            requestStoragePermission()
            return
        }
        if (!hasNotifications()) {
            requestNotificationPermission()
            return
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manageStorageLauncher.launch(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        } else {
            runtimePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runtimePermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun proceed() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
