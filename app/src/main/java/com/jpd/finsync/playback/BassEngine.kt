package com.jpd.finsync.playback

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.un4seen.bass.BASS
import com.un4seen.bass.BASSmix

private const val TAG = "BassEngine"
private const val DEFAULT_DEVICE = -1
// Android devices mix at 48 kHz; BASSmix resamples every source to the mixer's rate.
private const val MIXER_SAMPLE_RATE = 48_000
private const val STEREO = 2
private const val MS_PER_SECOND = 1_000.0
// BASS tries plugins in load order for each file (BASS_PluginLoad docs).
private val PLUGINS = listOf("bassflac", "bassalac", "bass_aac", "bassopus", "bassape", "basswv")

/**
 * Thin wrapper over BASS. Files are decoded into a queued BASSmix mixer (BASS_MIXER_QUEUE), so
 * a queued next track follows the current one with no gap. Call it on the main thread only;
 * [onTrackEnded] is delivered there too.
 */
class BassEngine(private val nativeLibraryDir: String) {

    /**
     * The current track's data has ended. [nextStarted] says whether a queued track took over.
     * The switch happens when the data ends, up to one playback buffer before it's heard.
     */
    var onTrackEnded: ((nextStarted: Boolean) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialised = false
    private var mixer = 0
    private var current = 0
    private var next = 0
    // Bumped whenever sources are replaced, so end syncs from old sources are ignored.
    private var generation = 0
    private var lastPositionMs = 0L

    private val endSync = BASS.SYNCPROC { _, channel, _, user ->
        // A mixtime sync runs on BASS's mixing thread, so hand it to the main thread.
        val syncGeneration = user as? Int ?: return@SYNCPROC
        mainHandler.post { onSourceEnded(channel, syncGeneration) }
    }

    /** Starts BASS once and loads the format plugins. False if playback can't work. */
    fun initialise(): Boolean {
        if (initialised) return true
        return try {
            initialiseBass()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "BASS libraries are missing", e)
            false
        }
    }

    /** Replaces whatever is loaded with [path] at [positionMs]. False if it can't be decoded. */
    fun load(path: String, positionMs: Long): Boolean {
        if (!initialised) return false
        freeSources()
        generation++
        val stream = createSource(path) ?: return false
        if (positionMs > 0) {
            val bytes = BASS.BASS_ChannelSeconds2Bytes(stream, positionMs / MS_PER_SECOND)
            BASS.BASS_ChannelSetPosition(stream, bytes, BASS.BASS_POS_BYTE)
        }
        if (!BASSmix.BASS_Mixer_StreamAddChannel(mixer, stream, 0)) {
            Log.w(TAG, "Couldn't add $path to the mixer: ${BASS.BASS_ErrorGetCode()}")
            BASS.BASS_StreamFree(stream)
            return false
        }
        current = stream
        lastPositionMs = positionMs
        // Flush the mixer's buffer so the new track is heard at once (BASSmix docs, "Seeking").
        BASS.BASS_ChannelSetPosition(mixer, 0, BASS.BASS_POS_BYTE)
        return true
    }

    /**
     * Queues [path] to follow the current track with no gap, replacing any queued track; null
     * just clears the queue. False if the file can't be decoded.
     */
    fun queueNext(path: String?): Boolean {
        if (next != 0) {
            BASS.BASS_StreamFree(next)
            next = 0
        }
        if (path == null) return true
        if (!initialised || current == 0) return false
        val stream = createSource(path) ?: return false
        val flags = BASSmix.BASS_MIXER_CHAN_NORAMPIN
        if (!BASSmix.BASS_Mixer_StreamAddChannel(mixer, stream, flags)) {
            Log.w(TAG, "Couldn't queue $path: ${BASS.BASS_ErrorGetCode()}")
            BASS.BASS_StreamFree(stream)
            return false
        }
        next = stream
        return true
    }

    fun play() {
        if (initialised) BASS.BASS_ChannelPlay(mixer, false)
    }

    fun pause() {
        if (initialised) BASS.BASS_ChannelPause(mixer)
    }

    /** Stops output and frees the tracks; the mixer stays ready for the next load. */
    fun stop() {
        if (!initialised) return
        BASS.BASS_ChannelStop(mixer)
        freeSources()
        generation++
    }

    /** The current track's position as heard (BASSmix allows for the playback buffer). */
    fun positionMs(): Long {
        if (current == 0) return lastPositionMs
        val bytes = BASSmix.BASS_Mixer_ChannelGetPosition(current, BASS.BASS_POS_BYTE)
        if (bytes >= 0) {
            val seconds = BASS.BASS_ChannelBytes2Seconds(current, bytes)
            lastPositionMs = (seconds * MS_PER_SECOND).toLong()
        }
        return lastPositionMs
    }

    /** The current track's length in ms as decoded, or 0 when nothing is loaded. */
    fun durationMs(): Long {
        if (current == 0) return 0L
        val bytes = BASS.BASS_ChannelGetLength(current, BASS.BASS_POS_BYTE)
        if (bytes < 0) return 0L
        return (BASS.BASS_ChannelBytes2Seconds(current, bytes) * MS_PER_SECOND).toLong()
    }

    fun setVolume(volume: Float) {
        if (initialised) BASS.BASS_ChannelSetAttribute(mixer, BASS.BASS_ATTRIB_VOL, volume)
    }

    fun release() {
        if (!initialised) return
        mainHandler.removeCallbacksAndMessages(null)
        freeSources()
        BASS.BASS_StreamFree(mixer)
        mixer = 0
        BASS.BASS_Free()
        initialised = false
    }

    private fun initialiseBass(): Boolean {
        val started = BASS.BASS_Init(DEFAULT_DEVICE, MIXER_SAMPLE_RATE, 0) ||
            BASS.BASS_ErrorGetCode() == BASS.BASS_ERROR_ALREADY
        if (!started) {
            Log.e(TAG, "BASS_Init failed: ${BASS.BASS_ErrorGetCode()}")
            return false
        }
        PLUGINS.forEach { name ->
            if (BASS.BASS_PluginLoad("$nativeLibraryDir/lib$name.so", 0) == 0) {
                Log.w(TAG, "Plugin $name not loaded: ${BASS.BASS_ErrorGetCode()}")
            }
        }
        val flags = BASS.BASS_SAMPLE_FLOAT or BASSmix.BASS_MIXER_QUEUE
        mixer = BASSmix.BASS_Mixer_StreamCreate(MIXER_SAMPLE_RATE, STEREO, flags)
        if (mixer == 0) {
            Log.e(TAG, "Mixer creation failed: ${BASS.BASS_ErrorGetCode()}")
            BASS.BASS_Free()
            return false
        }
        initialised = true
        return true
    }

    private fun createSource(path: String): Int? {
        val flags = BASS.BASS_STREAM_DECODE or BASS.BASS_SAMPLE_FLOAT
        val stream = BASS.BASS_StreamCreateFile(path, 0, 0, flags)
        if (stream == 0) {
            Log.w(TAG, "Can't decode $path: ${BASS.BASS_ErrorGetCode()}")
            return null
        }
        // On a decoding channel this is a mixtime sync: it fires as soon as the data runs out.
        val syncType = BASS.BASS_SYNC_END or BASS.BASS_SYNC_MIXTIME
        BASS.BASS_ChannelSetSync(stream, syncType, 0, endSync, generation)
        return stream
    }

    private fun freeSources() {
        if (next != 0) BASS.BASS_StreamFree(next)
        if (current != 0) BASS.BASS_StreamFree(current)
        next = 0
        current = 0
    }

    private fun onSourceEnded(channel: Int, syncGeneration: Int) {
        if (syncGeneration != generation || channel != current) return
        BASS.BASS_StreamFree(current)
        val nextStarted = next != 0
        current = next
        next = 0
        if (nextStarted) lastPositionMs = 0L
        onTrackEnded?.invoke(nextStarted)
    }
}
