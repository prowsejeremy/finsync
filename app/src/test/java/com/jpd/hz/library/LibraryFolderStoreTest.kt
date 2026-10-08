package com.jpd.hz.library

import org.junit.Assert.assertEquals
import org.junit.Test

private const val ROOT = "/storage/emulated/0"

class LibraryFolderStoreTest {

    @Test
    fun `a folder in shared storage is named from there`() {
        assertEquals("Media/hz", displayPathOf("$ROOT/Media/hz", ROOT))
        assertEquals("Media/hz", displayPathOf("$ROOT/Media/hz", "$ROOT/"))
    }

    @Test
    fun `the storage root itself and other folders keep their whole path`() {
        assertEquals(ROOT, displayPathOf(ROOT, ROOT))
        assertEquals("/storage/1234-ABCD/Music", displayPathOf("/storage/1234-ABCD/Music", ROOT))
        assertEquals("$ROOT-other/x", displayPathOf("$ROOT-other/x", ROOT))
    }
}
