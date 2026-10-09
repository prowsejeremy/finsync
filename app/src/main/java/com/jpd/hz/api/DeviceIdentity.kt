package com.jpd.hz.api

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.UUID

private const val TAG = "DeviceIdentity"
private const val FILE_NAME = "device_id"

/**
 * This install's Jellyfin device ID (spec "Sign-in health", decision 1): a random UUID made once
 * and kept in [folder]. Jellyfin ends a device's other sessions when it signs in again, so two
 * installs must never share one.
 */
class DeviceIdentity(private val folder: File) {

    /** The saved ID, or a new one saved now when the file is missing or blank. */
    @Synchronized
    fun id(): String {
        val file = File(folder, FILE_NAME)
        val saved = if (file.isFile) file.readText().trim() else ""
        if (saved.isNotEmpty()) return saved
        val made = UUID.randomUUID().toString()
        try {
            folder.mkdirs()
            file.writeText(made)
        } catch (e: IOException) {
            // Kept for this run only; the next run makes another, and signs in again at worst.
            Log.w(TAG, "Couldn't save the device ID", e)
        }
        return made
    }

    companion object {
        @Volatile
        private var cached: String? = null

        /** The app's ID, from noBackupFilesDir, which Android never backs up or restores. */
        fun of(context: Context): String =
            cached ?: synchronized(this) {
                cached ?: DeviceIdentity(context.applicationContext.noBackupFilesDir).id()
                    .also { cached = it }
            }
    }
}
