package com.jpd.finsync.library

import org.junit.Assert.assertFalse
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
}
