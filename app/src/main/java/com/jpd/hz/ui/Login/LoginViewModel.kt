package com.jpd.hz.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.hz.R
import com.jpd.hz.api.QuickConnectPoll
import com.jpd.hz.api.QuickConnectStart
import com.jpd.hz.auth.JellyfinRepository
import com.jpd.hz.auth.Result
import com.jpd.hz.library.SyncSelections
import com.jpd.hz.model.ServerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// How often a shown code is checked (spec "Quick Connect sign-in", decision 6).
private const val POLL_INTERVAL_MS = 5_000L

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = JellyfinRepository(app)

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    // Quick Connect first; the links swap the forms. Held here so a rotation keeps it.
    private val _method = MutableLiveData(SignInMethod.QUICK_CONNECT)
    val method: LiveData<SignInMethod> = _method

    private val _quickConnect = MutableLiveData<QuickConnectStep>(QuickConnectStep.Ready)
    val quickConnect: LiveData<QuickConnectStep> = _quickConnect

    private var quickConnectJob: Job? = null

    sealed class LoginState {
        object Idle    : LoginState()
        object Loading : LoginState()
        data class Success(val config: ServerConfig) : LoginState()
        data class Error(val message: String)         : LoginState()
    }

    enum class SignInMethod { QUICK_CONNECT, PASSWORD }

    /** The Quick Connect form: ready; busy getting a code or signing in; or showing a code. */
    sealed class QuickConnectStep {
        object Ready : QuickConnectStep()
        object Busy  : QuickConnectStep()
        data class Waiting(val code: String) : QuickConnectStep()
    }

    fun login(serverUrl: String, username: String, password: String) {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("All fields are required")
            return
        }

        val normalizedUrl = normalizeUrl(serverUrl)

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            when (val result = repo.login(normalizedUrl, username, password)) {
                is Result.Success -> signedIn(result.data)
                is Result.Error   -> _loginState.postValue(LoginState.Error(result.message))
            }
        }
    }

    fun useMethod(method: SignInMethod) {
        _method.value = method
    }

    /** Asks [serverUrl] for a code, shows it, and signs in once it's approved (decision 6). */
    fun getCode(serverUrl: String) {
        if (serverUrl.isBlank()) {
            _loginState.value = LoginState.Error(text(R.string.quick_connect_url_required))
            return
        }
        val url = normalizeUrl(serverUrl)
        _quickConnect.value = QuickConnectStep.Busy
        quickConnectJob = viewModelScope.launch {
            when (val start = repo.startQuickConnect(url)) {
                is QuickConnectStart.Started -> {
                    _quickConnect.value = QuickConnectStep.Waiting(start.code)
                    awaitApproval(url, start.secret)
                }
                QuickConnectStart.Off -> {
                    stopQuickConnect(text(R.string.quick_connect_off))
                    _method.value = SignInMethod.PASSWORD
                }
                is QuickConnectStart.Failed -> stopQuickConnect(start.message)
            }
        }
    }

    /** Stops waiting. The server forgets the code by itself. */
    fun cancelQuickConnect() {
        quickConnectJob?.cancel()
        _quickConnect.value = QuickConnectStep.Ready
    }

    /** The screen has shown the error, so a rotation doesn't show it again. */
    fun messageShown() {
        _loginState.value = LoginState.Idle
    }

    fun checkExistingLogin(): ServerConfig? = repo.getSavedConfig()

    // viewModelScope runs on the main thread, so the steps are set directly.
    private suspend fun awaitApproval(url: String, secret: String) {
        var poll = QuickConnectPoll.WAITING
        while (poll == QuickConnectPoll.WAITING) {
            delay(POLL_INTERVAL_MS)
            poll = repo.checkQuickConnect(url, secret)
        }
        when (poll) {
            QuickConnectPoll.APPROVED -> signInWithQuickConnect(url, secret)
            QuickConnectPoll.EXPIRED -> stopQuickConnect(text(R.string.quick_connect_expired))
            else -> stopQuickConnect(text(R.string.quick_connect_unreachable))
        }
    }

    private suspend fun signInWithQuickConnect(url: String, secret: String) {
        _quickConnect.value = QuickConnectStep.Busy
        when (val result = repo.signInWithQuickConnect(url, secret)) {
            is Result.Success -> signedIn(result.data)
            is Result.Error   -> stopQuickConnect(result.message)
        }
    }

    // Before any screen shows them: this server's choices, not the last one's.
    private fun signedIn(config: ServerConfig) {
        SyncSelections(getApplication()).useFor(config.serverId)
        _loginState.postValue(LoginState.Success(config))
    }

    // Back to Get code, saying why.
    private fun stopQuickConnect(message: String) {
        _quickConnect.value = QuickConnectStep.Ready
        _loginState.value = LoginState.Error(message)
    }

    private fun text(@StringRes id: Int): String = getApplication<Application>().getString(id)

    private fun normalizeUrl(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u.trimEnd('/')
    }
}
