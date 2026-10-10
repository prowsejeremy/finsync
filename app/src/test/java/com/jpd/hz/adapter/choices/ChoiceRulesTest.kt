package com.jpd.hz.adapter.choices

import com.jpd.hz.adapter.ChoiceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The choice rules for every kind: empty means none, `all` means every group, and what each
 * kind's Select all saves (adapter harness spec, H4).
 */
class ChoiceRulesTest {

    private val listed = listOf("a1", "a2", "a3")

    @Test
    fun `an empty choice chooses nothing, for every kind`() {
        assertFalse(ChoiceRules.isChosen("a1", emptySet()))
        assertFalse(ChoiceRules.selectsEvery(emptySet()))
    }

    @Test
    fun `a choice holding all chooses every group, even one it doesn't name`() {
        assertTrue(ChoiceRules.selectsEvery(setOf(ALL, "a2")))
        assertTrue(ChoiceRules.isChosen("a1", setOf(ALL, "a2")))
        assertTrue(ChoiceRules.isChosen("added-later", setOf(ALL)))
    }

    @Test
    fun `a specific choice chooses only the groups it names`() {
        assertTrue(ChoiceRules.isChosen("a2", setOf("a2")))
        assertFalse(ChoiceRules.isChosen("a1", setOf("a2")))
        assertFalse(ChoiceRules.selectsEvery(setOf("a2")))
    }

    @Test
    fun `nothing is saved from a list that never loaded, for every kind`() {
        for (kind in ChoiceKind.values()) {
            assertNull(kind.name, ChoiceRules.idsToSave(kind, emptySet(), emptyList(), setOf("a1")))
        }
    }

    @Test
    fun `every album or folder ticked saves all, so ones added later follow`() {
        for (kind in listOf(ChoiceKind.ALBUM, ChoiceKind.FOLDER)) {
            assertEquals(
                kind.name,
                setOf(ALL),
                ChoiceRules.idsToSave(kind, listed.toSet(), listed, emptySet())
            )
        }
    }

    @Test
    fun `some albums or folders ticked save their IDs, and none ticked saves none`() {
        for (kind in listOf(ChoiceKind.ALBUM, ChoiceKind.FOLDER)) {
            assertEquals(
                kind.name,
                setOf("a2"),
                ChoiceRules.idsToSave(kind, setOf("a2"), listed, setOf(ALL))
            )
            assertEquals(
                kind.name,
                emptySet<String>(),
                ChoiceRules.idsToSave(kind, emptySet(), listed, setOf("a1"))
            )
        }
    }

    @Test
    fun `playlists and books save the ticked IDs, even with every one ticked`() {
        for (kind in listOf(ChoiceKind.PLAYLIST, ChoiceKind.BOOK)) {
            assertEquals(
                kind.name,
                listed.toSet(),
                ChoiceRules.idsToSave(kind, listed.toSet(), listed, emptySet())
            )
            assertEquals(
                kind.name,
                setOf("a2"),
                ChoiceRules.idsToSave(kind, setOf("a2"), listed, setOf("a1"))
            )
        }
    }

    @Test
    fun `a saved choice the screen didn't list is kept`() {
        // A playlist whose entries didn't load isn't listed; Select mustn't drop it.
        for (kind in ChoiceKind.values()) {
            assertEquals(
                kind.name,
                setOf("a2", "p9"),
                ChoiceRules.idsToSave(kind, setOf("a2"), listed, setOf("a1", "p9"))
            )
        }
    }

    @Test
    fun `all ticks every listed group on opening`() {
        assertEquals(listed.toSet(), ChoiceRules.tickedOf(setOf(ALL), listed))
    }

    @Test
    fun `a specific choice ticks the listed groups it names, and drops ones no longer listed`() {
        assertEquals(setOf("a2"), ChoiceRules.tickedOf(setOf("a2", "gone"), listed))
        assertEquals(emptySet<String>(), ChoiceRules.tickedOf(emptySet(), listed))
    }

    @Test
    fun `a connection with every kind empty has nothing chosen`() {
        assertTrue(ChoiceRules.nothingChosen(listOf(emptySet(), emptySet())))
        assertTrue(ChoiceRules.nothingChosen(emptyList()))
        assertFalse(ChoiceRules.nothingChosen(listOf(emptySet(), setOf("b1"))))
        assertFalse(ChoiceRules.nothingChosen(listOf(setOf(ALL))))
    }
}
