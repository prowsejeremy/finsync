package com.jpd.hz.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.R
import com.jpd.hz.databinding.ActivityLoginBinding
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The sign-in screen opens on Quick Connect, LoginViewModel's default, so nothing flashes. */
@RunWith(RobolectricTestRunner::class)
class LoginLayoutTest {

    @Test
    fun theScreenOpensOnQuickConnect() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Hz_Login
        )
        val binding = ActivityLoginBinding.inflate(LayoutInflater.from(context))

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
