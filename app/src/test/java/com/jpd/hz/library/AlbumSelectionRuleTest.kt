package com.jpd.hz.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumSelectionRuleTest {

    @Test
    fun `empty selection selects every album`() {
        assertTrue(isAlbumSelected("a1", emptySet()))
    }

    @Test
    fun `selection containing all selects every album`() {
        assertTrue(isAlbumSelected("a1", setOf("all", "a2")))
    }

    @Test
    fun `specific selection selects only the listed albums`() {
        assertTrue(isAlbumSelected("a2", setOf("a2")))
        assertFalse(isAlbumSelected("a1", setOf("a2")))
    }

    @Test
    fun `nothing is saved from a list that never loaded`() {
        assertNull(albumIdsToSave(emptySet(), total = 0))
    }

    @Test
    fun `every album ticked saves all`() {
        assertEquals(setOf("all"), albumIdsToSave(setOf("a1", "a2"), total = 2))
    }

    @Test
    fun `some albums ticked saves their IDs`() {
        assertEquals(setOf("a2"), albumIdsToSave(setOf("a2"), total = 2))
    }
}
