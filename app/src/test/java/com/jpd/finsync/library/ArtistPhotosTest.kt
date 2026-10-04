package com.jpd.finsync.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistPhotosTest {

    @Test
    fun `photos of artists still needed are kept`() {
        assertEquals(
            emptyList<String>(),
            stalePhotoFileNames(listOf("a1.jpg", "a2.jpg"), setOf("a1", "a2"))
        )
    }

    @Test
    fun `photos no downloaded album needs and unfinished downloads go`() {
        assertEquals(
            listOf("gone.jpg", "a1.jpg.part"),
            stalePhotoFileNames(listOf("a1.jpg", "gone.jpg", "a1.jpg.part"), setOf("a1"))
        )
    }
}
