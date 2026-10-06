package com.jpd.hz.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.media3.common.Player

// How quiet hz gets while another app briefly plays over it, e.g. a notification sound.
private const val DUCK_VOLUME = 0.2f
private const val FULL_VOLUME = 1f
private const val WAKE_LOCK_TAG = "hz:playback"

/**
 * Audio focus, "becoming noisy" (headphones unplugged) and the wake lock, driven by the player.
 * Media3's session layer does none of this for a custom Player.
 */
class PlaybackFocus(
    private val context: Context,
    private val player: Player,
    private val engine: BassEngine
) : Player.Listener {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        .apply { setReferenceCounted(false) }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setOnAudioFocusChangeListener { change -> onAudioFocusChange(change) }
        .build()
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) player.pause()
        }
    }
    private var hasFocus = false
    // Set while a transient loss has paused playback, so focus is kept and play resumes on gain.
    private var resumeOnGain = false
    private var noisyRegistered = false

    init {
        player.addListener(this)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) {
            resumeOnGain = false
            requestFocus()
        } else if (!resumeOnGain) {
            abandonFocus()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            wakeLock.acquire()
            registerNoisy()
        } else {
            if (wakeLock.isHeld) wakeLock.release()
            unregisterNoisy()
        }
    }

    fun release() {
        player.removeListener(this)
        resumeOnGain = false
        abandonFocus()
        unregisterNoisy()
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun requestFocus() {
        if (hasFocus) return
        hasFocus = audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        // Delayed focus isn't enabled, so anything but GRANTED means another app (a call) has it.
        if (!hasFocus) player.pause()
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
        engine.setVolume(FULL_VOLUME)
    }

    private fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnGain = false
                player.pause()
                abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (player.playWhenReady) {
                resumeOnGain = true
                player.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> engine.setVolume(DUCK_VOLUME)
            AudioManager.AUDIOFOCUS_GAIN -> {
                engine.setVolume(FULL_VOLUME)
                if (resumeOnGain) {
                    resumeOnGain = false
                    player.play()
                }
            }
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        context.unregisterReceiver(noisyReceiver)
        noisyRegistered = false
    }
}
