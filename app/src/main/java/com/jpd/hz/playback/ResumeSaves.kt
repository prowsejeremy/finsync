package com.jpd.hz.playback

import androidx.media3.common.Player

private val SAVE_EVENTS = intArrayOf(
    Player.EVENT_PLAY_WHEN_READY_CHANGED,
    Player.EVENT_MEDIA_ITEM_TRANSITION,
    Player.EVENT_POSITION_DISCONTINUITY,
    Player.EVENT_TIMELINE_CHANGED,
    // A new chapter changes only the current item's title (BassPlayer's chapter check). Saving
    // then keeps the book page's green chapter with the Player's, not up to 10 s behind it.
    Player.EVENT_MEDIA_METADATA_CHANGED,
    Player.EVENT_REPEAT_MODE_CHANGED,
    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
)

/**
 * Whether a batch of player events saves the queue and the current book's place (3b
 * "Progress"). [hasEvent] says whether the batch holds an event; [playbackState] is the player's
 * state after it. Reaching the end marks a book Finished (spec "Finishing"). Other state changes,
 * such as logout's stop, don't save, so they can't undo logout's clearing.
 */
fun savesResume(hasEvent: (Int) -> Boolean, playbackState: Int): Boolean {
    val ended = hasEvent(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
        playbackState == Player.STATE_ENDED
    return ended || SAVE_EVENTS.any(hasEvent)
}
