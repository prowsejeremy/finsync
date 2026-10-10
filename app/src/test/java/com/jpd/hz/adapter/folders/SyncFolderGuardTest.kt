package com.jpd.hz.adapter.folders

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NAME = "kurage"
private const val LIBRARY = "/storage/emulated/0/Media/hz"
private const val FOLDER = "$LIBRARY/kurage"

/**
 * The folder guard a run checks before it touches a file, with messages that name the connection
 * (adapter harness spec, "The sync run", step 2, and H9.3).
 */
class SyncFolderGuardTest {

    @Test
    fun `a folder inside the library may sync, whether or not it exists yet`() {
        assertNull(syncFolderProblemOf(NAME, FOLDER, true, hasRecords = true, library = LIBRARY))
        assertNull(syncFolderProblemOf(NAME, FOLDER, false, hasRecords = false, library = LIBRARY))
    }

    @Test
    fun `a folder that's gone while the connection's records remain may not sync`() {
        val problem = syncFolderProblemOf(NAME, FOLDER, false, hasRecords = true, library = LIBRARY)

        assertNotNull(problem)
        assertTrue(problem, problem!!.contains("kurage's folder"))
    }

    @Test
    fun `a folder that is or holds the library may not sync`() {
        val same = syncFolderProblemOf(NAME, LIBRARY, true, false, LIBRARY)
        val holder = syncFolderProblemOf(NAME, "/storage/emulated/0/Media", true, false, LIBRARY)

        for (problem in listOf(same, holder)) {
            assertNotNull(problem)
            assertTrue(problem, problem!!.startsWith("kurage's folder"))
            assertTrue(problem, problem.contains("holds the Library folder"))
        }
    }
}
