package com.jpd.hz.playback

import android.util.Log
import com.un4seen.bass.BASS

private const val TAG = "BassPlugins"
// BASS tries plugins in load order for each file (BASS_PluginLoad docs).
private val PLUGINS = listOf("bassflac", "bassalac", "bass_aac", "bassopus", "bassape", "basswv")

/**
 * Loads the format add-ons from [nativeLibraryDir]. Plugins belong to the whole process, so the
 * engine and the scanner's decode check share them: whichever starts BASS second sees
 * BASS_ERROR_ALREADY, which isn't a failure.
 */
fun loadBassPlugins(nativeLibraryDir: String) {
    PLUGINS.forEach { name ->
        val loaded = BASS.BASS_PluginLoad("$nativeLibraryDir/lib$name.so", 0) != 0 ||
            BASS.BASS_ErrorGetCode() == BASS.BASS_ERROR_ALREADY
        if (!loaded) Log.w(TAG, "Plugin $name not loaded: ${BASS.BASS_ErrorGetCode()}")
    }
}
