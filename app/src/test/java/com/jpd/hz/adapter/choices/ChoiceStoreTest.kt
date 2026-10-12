package com.jpd.hz.adapter.choices

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.adapter.ChoiceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PREFS = "settings"
private const val KURAGE = "jellyfin:3f2a"
private const val HOME = "plex:77b0"

/**
 * Each connection's choices, against the real settings prefs (adapter harness spec, "Saved
 * settings"). The old `useFor` swap between servers went with the per-connection keys.
 */
@RunWith(RobolectricTestRunner::class)
class ChoiceStoreTest {

    private lateinit var context: Context
    private lateinit var store: ChoiceStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        store = ChoiceStore(context)
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Test
    fun `a connection starts with nothing chosen, of every kind`() {
        for (kind in ChoiceKind.values()) {
            assertTrue(kind.name, store.chosen(KURAGE, kind).isEmpty())
        }
    }

    @Test
    fun `a choice reads back as saved, all included`() {
        store.setChosen(KURAGE, ChoiceKind.PLAYLIST, setOf("p1", "p2"))
        store.setChosen(KURAGE, ChoiceKind.ALBUM, setOf(ALL))

        assertEquals(setOf("p1", "p2"), store.chosen(KURAGE, ChoiceKind.PLAYLIST))
        assertEquals(setOf(ALL), ChoiceStore(context).chosen(KURAGE, ChoiceKind.ALBUM))
    }

    @Test
    fun `saving again replaces the choice, and an empty one chooses nothing`() {
        store.setChosen(KURAGE, ChoiceKind.BOOK, setOf("b1"))
        store.setChosen(KURAGE, ChoiceKind.BOOK, setOf("b2"))
        assertEquals(setOf("b2"), store.chosen(KURAGE, ChoiceKind.BOOK))

        store.setChosen(KURAGE, ChoiceKind.BOOK, emptySet())
        assertTrue(store.chosen(KURAGE, ChoiceKind.BOOK).isEmpty())
    }

    @Test
    fun `each connection and each kind has its own key`() {
        store.setChosen(KURAGE, ChoiceKind.ALBUM, setOf("a1"))
        store.setChosen(KURAGE, ChoiceKind.BOOK, setOf("b1"))
        store.setChosen(HOME, ChoiceKind.ALBUM, setOf("a9"))

        assertEquals(setOf("a1"), store.chosen(KURAGE, ChoiceKind.ALBUM))
        assertEquals(setOf("b1"), store.chosen(KURAGE, ChoiceKind.BOOK))
        assertTrue(store.chosen(KURAGE, ChoiceKind.PLAYLIST).isEmpty())
        assertEquals(setOf("a9"), store.chosen(HOME, ChoiceKind.ALBUM))
        assertTrue(store.chosen(HOME, ChoiceKind.BOOK).isEmpty())
        assertEquals(
            setOf("a1"),
            prefs().getStringSet("choices:jellyfin:3f2a:album", null)
        )
        assertEquals(setOf("a9"), prefs().getStringSet("choices:plex:77b0:album", null))
    }

    @Test
    fun `a run reads every kind of a connection's choice at once`() {
        store.setChosen(KURAGE, ChoiceKind.ALBUM, setOf(ALL))
        store.setChosen(KURAGE, ChoiceKind.PLAYLIST, setOf("p1"))

        assertEquals(
            mapOf(
                ChoiceKind.ALBUM to setOf(ALL),
                ChoiceKind.PLAYLIST to setOf("p1"),
                ChoiceKind.BOOK to emptySet()
            ),
            store.chosen(KURAGE, listOf(ChoiceKind.ALBUM, ChoiceKind.PLAYLIST, ChoiceKind.BOOK))
        )
    }

    @Test
    fun `the old selection keys are no longer read`() {
        prefs().edit()
            .putStringSet("selected_albums", setOf("a1"))
            .putStringSet("selected_playlists", setOf("p1"))
            .commit()

        assertTrue(store.chosen(KURAGE, ChoiceKind.ALBUM).isEmpty())
        assertTrue(store.chosen(KURAGE, ChoiceKind.PLAYLIST).isEmpty())
    }
}
