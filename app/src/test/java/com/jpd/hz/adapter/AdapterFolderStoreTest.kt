package com.jpd.hz.adapter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PREFS = "settings"

/** `adapter_folders` in the settings prefs (spec "Saved settings"). */
@RunWith(RobolectricTestRunner::class)
class AdapterFolderStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `each server's folder is saved in the spec's format, and saving again replaces it`() {
        val store = AdapterFolderStore(context)

        assertNull(store.pathFor("jellyfin", "3f2a"))
        store.save(AdapterFolder("jellyfin", "3f2a", "/storage/emulated/0/Media/hz"))
        store.save(AdapterFolder("jellyfin", "77b0", "/storage/emulated/0/Media/hz/other"))
        store.save(AdapterFolder("jellyfin", "3f2a", "/storage/emulated/0/Media/hz/kurage"))

        assertEquals("/storage/emulated/0/Media/hz/kurage", store.pathFor("jellyfin", "3f2a"))
        assertEquals(
            "jellyfin:77b0|/storage/emulated/0/Media/hz/other\n" +
                "jellyfin:3f2a|/storage/emulated/0/Media/hz/kurage",
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("adapter_folders", null)
        )
    }
}
