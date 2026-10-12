package com.jpd.hz.platform.jellyfin.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.R
import com.jpd.hz.databinding.ActivityJellyfinSignInBinding
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Jellyfin's sign-in screen opens on Quick Connect, JellyfinSignInViewModel's default, so nothing
 * flashes (adapter harness spec, "Screens": moved unchanged into Jellyfin's package).
 */
@RunWith(RobolectricTestRunner::class)
class JellyfinSignInLayoutTest {

    @Test
    fun `the screen opens on Quick Connect`() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Hz_Login
        )
        val binding = ActivityJellyfinSignInBinding.inflate(LayoutInflater.from(context))

        assertEquals(View.VISIBLE, binding.quickConnectForm.visibility)
        assertEquals(View.VISIBLE, binding.btnGetCode.visibility)
        assertEquals(View.VISIBLE, binding.linkUsePassword.visibility)
        assertEquals(View.GONE, binding.codeGroup.visibility)
        assertEquals(View.GONE, binding.progressQuickConnect.visibility)
        assertEquals(View.GONE, binding.btnCancelCode.visibility)
        assertEquals(View.GONE, binding.passwordForm.visibility)
        assertEquals(View.VISIBLE, binding.linkUseQuickConnect.visibility)
    }
}
