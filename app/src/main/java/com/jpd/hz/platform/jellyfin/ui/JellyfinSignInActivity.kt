package com.jpd.hz.platform.jellyfin.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.jpd.hz.appearance.applyAccentOverlay
import com.jpd.hz.databinding.ActivityJellyfinSignInBinding
import com.jpd.hz.platform.jellyfin.ui.JellyfinSignInViewModel.QuickConnectStep
import com.jpd.hz.platform.jellyfin.ui.JellyfinSignInViewModel.SignInMethod
import com.google.android.material.snackbar.Snackbar

/**
 * Signing in to Jellyfin, opened from Adapters → Jellyfin, which it returns to (T4). Quick
 * Connect shows first; a link swaps in the username and password form (spec "Quick Connect
 * sign-in").
 */
class JellyfinSignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJellyfinSignInBinding
    private val viewModel: JellyfinSignInViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the layout is inflated, so the fields' focus colour is the saved accent.
        applyAccentOverlay()
        binding = ActivityJellyfinSignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        binding.editPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.btnGetCode.setOnClickListener {
            viewModel.getCode(binding.editServerUrl.text.toString())
        }
        binding.btnCancelCode.setOnClickListener { viewModel.cancelQuickConnect() }
        binding.linkUsePassword.setOnClickListener { viewModel.useMethod(SignInMethod.PASSWORD) }
        binding.linkUseQuickConnect.setOnClickListener {
            viewModel.useMethod(SignInMethod.QUICK_CONNECT)
        }
    }

    private fun attemptLogin() {
        val url      = binding.editServerUrl.text.toString()
        val username = binding.editUsername.text.toString()
        val password = binding.editPassword.text.toString()
        viewModel.login(url, username, password)
    }

    private fun observeViewModel() {
        viewModel.method.observe(this) { method ->
            binding.quickConnectForm.isVisible = method == SignInMethod.QUICK_CONNECT
            binding.passwordForm.isVisible = method == SignInMethod.PASSWORD
        }
        viewModel.quickConnect.observe(this, ::renderQuickConnect)
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is JellyfinSignInViewModel.LoginState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                    binding.linkUseQuickConnect.isEnabled = false
                }
                is JellyfinSignInViewModel.LoginState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    // Back to the Jellyfin page, whose activity reads the new sign-in on resume.
                    finish()
                }
                is JellyfinSignInViewModel.LoginState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    binding.linkUseQuickConnect.isEnabled = true
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    viewModel.messageShown()
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    binding.linkUseQuickConnect.isEnabled = true
                }
            }
        }
    }

    // While a code is out, neither the URL nor the form can change under it.
    private fun renderQuickConnect(step: QuickConnectStep) {
        val ready = step == QuickConnectStep.Ready
        val waiting = step is QuickConnectStep.Waiting
        binding.codeGroup.isVisible = waiting
        binding.tvQuickConnectCode.text = (step as? QuickConnectStep.Waiting)?.code
        binding.progressQuickConnect.isVisible = !ready
        binding.btnGetCode.isVisible = !waiting
        binding.btnGetCode.isEnabled = ready
        binding.btnCancelCode.isVisible = waiting
        binding.linkUsePassword.isVisible = ready
        binding.layoutServerUrl.isEnabled = ready
    }
}
