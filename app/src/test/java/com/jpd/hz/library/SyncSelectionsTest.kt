package com.jpd.hz.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val KURAGE = "server-kurage"
private const val OTHER = "server-other"

/** Each server's sync selections, against the real settings prefs (T4). */
@RunWith(RobolectricTestRunner::class)
class SyncSelectionsTest {

    private lateinit var context: Context
    private lateinit var selections: SyncSelections

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        selections = SyncSelections(context)
    }

    private fun prefs() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun albums(): Set<String>? = prefs().getStringSet("selected_albums", null)

    // What a choice screen saves: a few albums, a playlist and a book.
    private fun chooseKurages() {
        prefs().edit().putStringSet("selected_albums", setOf("a1", "a2")).commit()
        selections.setPlaylistIds(setOf("p1"))
        selections.setBookIds(setOf("b1"))
    }

    @Test
    fun `selections saved before T4 belong to the first server to ask`() {
        chooseKurages()

        selections.useFor(KURAGE)
        selections.useFor(KURAGE)

        assertEquals(setOf("a1", "a2"), albums())
        assertEquals(setOf("p1"), selections.playlistIds())
        assertEquals(setOf("b1"), selections.bookIds())
    }

    @Test
    fun `another server starts as a fresh install does`() {
        selections.useFor(KURAGE)
        chooseKurages()

        selections.useFor(OTHER)

        // No album key means every album; no playlist or book key means none.
        assertFalse(prefs().contains("selected_albums"))
        assertTrue(selections.playlistIds().isEmpty())
        assertTrue(selections.bookIds().isEmpty())
    }

    @Test
    fun `signing in to another server and back brings each one's selections back`() {
        selections.useFor(KURAGE)
        chooseKurages()
        selections.useFor(OTHER)
        prefs().edit().putStringSet("selected_albums", setOf("all")).commit()
        selections.setBookIds(setOf("other-book"))

        selections.useFor(KURAGE)

        assertEquals(setOf("a1", "a2"), albums())
        assertEquals(setOf("p1"), selections.playlistIds())
        assertEquals(setOf("b1"), selections.bookIds())

        selections.useFor(OTHER)

        assertEquals(setOf("all"), albums())
        assertTrue(selections.playlistIds().isEmpty())
        assertEquals(setOf("other-book"), selections.bookIds())
    }
}
