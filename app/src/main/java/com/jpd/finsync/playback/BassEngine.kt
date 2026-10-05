package com.jpd.finsync.playback

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.un4seen.bass.BASS
import com.un4seen.bass.BASS_FX
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
 * a queued next track follows the current one with no gap. A book plays through a BASS_FX tempo
 * stream wrapped round its decoder, so its speed changes without changing its pitch (3b). Call
 * it on the main thread only; [onTrackEnded] is delivered there too.
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

    /**
     * Replaces whatever is loaded with [path] at [positionMs]. A [speed] plays it through a tempo
     * stream at that speed (a book); null plays it as decoded (music). False if it can't be
     * decoded.
     */
    fun load(path: String, positionMs: Long, speed: Float? = null): Boolean {
        if (!initialised) return false
        freeSources()
        generation++
        val stream = createSource(path, speed) ?: return false
        if (positionMs > 0) {
            val bytes = bytesWithinLength(stream, positionMs)
            // Refused, the stream would start at 0:00 and a book would lose its place.
            if (!BASS.BASS_ChannelSetPosition(stream, bytes, BASS.BASS_POS_BYTE)) {
                Log.w(TAG, "Couldn't start $path at $positionMs ms: ${BASS.BASS_ErrorGetCode()}")
                BASS.BASS_StreamFree(stream)
                return false
            }
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
     * just clears the queue. False if the file can't be decoded. Only music is queued: a book
     * plays alone.
     */
    fun queueNext(path: String?): Boolean {
        if (next != 0) {
            BASS.BASS_StreamFree(next)
            next = 0
        }
        if (path == null) return true
        if (!initialised || current == 0) return false
        val stream = createSource(path, speed = null) ?: return false
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

    /**
     * Moves the current track to [positionMs] without reopening its file (sub-project 2's minor
     * item), so skips in a long book are quick. A queued next track stays queued. False when
     * nothing is loaded or BASSmix refuses; the caller then reloads.
     */
    fun seek(positionMs: Long): Boolean {
        if (!initialised || current == 0) return false
        val bytes = bytesWithinLength(current, positionMs)
        // MIXER_RESET flushes the mixer's buffer, so the new position is heard at once.
        val mode = BASS.BASS_POS_BYTE or BASSmix.BASS_POS_MIXER_RESET
        if (!BASSmix.BASS_Mixer_ChannelSetPosition(current, bytes, mode)) {
            Log.w(TAG, "Seek failed: ${BASS.BASS_ErrorGetCode()}")
            return false
        }
        lastPositionMs = positionMs
        return true
    }

    // The catalogue's length can run past the decoded end, where BASS refuses to seek; the end
    // itself is fine, and a book then finishes normally (3b review).
    private fun bytesWithinLength(stream: Int, positionMs: Long): Long {
        val bytes = BASS.BASS_ChannelSeconds2Bytes(stream, positionMs / MS_PER_SECOND)
        val length = BASS.BASS_ChannelGetLength(stream, BASS.BASS_POS_BYTE)
        return if (length >= 0) minOf(bytes, length) else bytes
    }

    /** Sets the current book's speed. Only a book's tempo stream takes it; callers check. */
    fun setSpeed(speed: Float) {
        if (current == 0) return
        val tempo = tempoPercentFor(speed)
        if (!BASS.BASS_ChannelSetAttribute(current, BASS_FX.BASS_ATTRIB_TEMPO, tempo)) {
            Log.w(TAG, "Speed change failed: ${BASS.BASS_ErrorGetCode()}")
        }
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

    private fun createSource(path: String, speed: Float?): Int? {
        val flags = BASS.BASS_STREAM_DECODE or BASS.BASS_SAMPLE_FLOAT
        val decoder = BASS.BASS_StreamCreateFile(path, 0, 0, flags)
        if (decoder == 0) {
            Log.w(TAG, "Can't decode $path: ${BASS.BASS_ErrorGetCode()}")
            return null
        }
        val stream = if (speed == null) decoder else tempoStreamOf(decoder, speed) ?: return null
        // On a decoding channel this is a mixtime sync: it fires as soon as the data runs out.
        val syncType = BASS.BASS_SYNC_END or BASS.BASS_SYNC_MIXTIME
        BASS.BASS_ChannelSetSync(stream, syncType, 0, endSync, generation)
        return stream
    }

    /**
     * Wraps [decoder] in a BASS_FX tempo stream at [speed]. It decodes too, so the mixer takes it
     * like any source; FREESOURCE frees the decoder with it. Null if BASS_FX can't, and the
     * decoder is freed.
     */
    private fun tempoStreamOf(decoder: Int, speed: Float): Int? {
        val flags = BASS.BASS_STREAM_DECODE or BASS_FX.BASS_FX_FREESOURCE
        val tempo = try {
            BASS_FX.BASS_FX_TempoCreate(decoder, flags)
        } catch (e: LinkageError) {
            // A missing libbass_fx.so fails the class's loadLibrary: the book can't play.
            Log.e(TAG, "BASS_FX library is missing", e)
            0
        }
        if (tempo == 0) {
            Log.w(TAG, "Tempo stream failed: ${BASS.BASS_ErrorGetCode()}")
            BASS.BASS_StreamFree(decoder)
            return null
        }
        BASS.BASS_ChannelSetAttribute(tempo, BASS_FX.BASS_ATTRIB_TEMPO, tempoPercentFor(speed))
        return tempo
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
