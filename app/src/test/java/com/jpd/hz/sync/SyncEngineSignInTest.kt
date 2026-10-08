package com.jpd.hz.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.adapter.AdapterFolderStore
import com.jpd.hz.library.LibraryFolderStore
import com.jpd.hz.model.ServerConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A sync asked for before a sign-out runs nothing after it (T4): no settle, no folder saved, no
 * state. Without the check, the settle would save Jellyfin's folder and the fetch would fail.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEngineSignInTest {

    private lateinit var context: Context
    private val config = ServerConfig(
        serverUrl = "http://kurage", serverId = "server-1", serverName = "kurage",
        userId = "user", username = "me", accessToken = "token"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun assertNothingRan(stateBefore: Any) {
        assertSame(stateBefore, SyncEngine.syncState.value)
        assertNull(LibraryFolderStore(context).saved())
        assertTrue(AdapterFolderStore(context).all().isEmpty())
    }

    @Test
    fun `a sync after signing out runs nothing`() = runBlocking {
        val before = SyncEngine.syncState.value

        SyncEngine.syncLibrary(context, config, savedConfig = { null })

        assertNothingRan(before)
    }

    @Test
    fun `a sync from before signing in again runs nothing`() = runBlocking {
        val before = SyncEngine.syncState.value

        SyncEngine.syncLibrary(context, config, savedConfig = { config.copy(accessToken = "new") })

        assertNothingRan(before)
    }
}
