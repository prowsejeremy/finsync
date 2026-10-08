package com.jpd.hz.library.scan

import android.util.Log
import com.jpd.hz.playback.loadBassPlugins
import com.un4seen.bass.BASS

private const val TAG = "BassDecodeCheck"
// BASS's "no sound" device: enough for decoding, and never the engine's output.
private const val NO_SOUND = 0
private const val SAMPLE_RATE = 48_000

/**
 * Opens a decode-only stream and frees it straight away. BASS picks the device per thread, so
 * the stream sits on "no sound" whether or not the playback engine has started its own device
 * (checked on the phone, beside a playing engine and after it freed BASS). The device is never
 * freed: BassEngine's BASS_Free frees only its own.
 */
class BassDecodeCheck(private val nativeLibraryDir: String) : DecodeCheck {

    override fun canDecode(path: String): Boolean? {
        if (!startNoSoundDevice()) return null
        // On this thread only; the engine's thread keeps its device.
        if (!BASS.BASS_SetDevice(NO_SOUND)) {
            Log.w(TAG, "Couldn't use the no-sound device: ${BASS.BASS_ErrorGetCode()}")
            return null
        }
        val flags = BASS.BASS_STREAM_DECODE or BASS.BASS_SAMPLE_FLOAT
        val stream = BASS.BASS_StreamCreateFile(path, 0, 0, flags)
        if (stream == 0) return false
        BASS.BASS_StreamFree(stream)
        return true
    }

    private fun startNoSoundDevice(): Boolean = synchronized(BassDecodeCheck::class.java) {
        if (started) return true
        started = try {
            val ready = BASS.BASS_Init(NO_SOUND, SAMPLE_RATE, 0) ||
                BASS.BASS_ErrorGetCode() == BASS.BASS_ERROR_ALREADY
            if (ready) loadBassPlugins(nativeLibraryDir)
            ready
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "BASS libraries are missing", e)
            false
        }
        started
    }

    private companion object {
        // Once per process, like the plugins.
        var started = false
    }
}
