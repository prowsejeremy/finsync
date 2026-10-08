package com.jpd.hz.sync

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val LIBRARY = "/storage/emulated/0/Media/hz"
private const val FOLDER = "$LIBRARY/kurage"

class SyncFolderGuardTest {

    @Test
    fun `a folder inside the library may sync, whether or not it exists yet`() {
        assertNull(syncFolderProblemOf(FOLDER, true, hasRecords = true, library = LIBRARY))
        assertNull(syncFolderProblemOf(FOLDER, false, hasRecords = false, library = LIBRARY))
    }

    @Test
    fun `a folder that's gone while the server's records remain may not sync`() {
        assertNotNull(syncFolderProblemOf(FOLDER, false, hasRecords = true, library = LIBRARY))
    }

    @Test
    fun `a folder that is or holds the library may not sync`() {
        assertNotNull(syncFolderProblemOf(LIBRARY, true, false, LIBRARY))
        assertNotNull(syncFolderProblemOf("/storage/emulated/0/Media", true, false, LIBRARY))
    }
}
