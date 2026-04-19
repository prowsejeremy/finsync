package com.jpd.finsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.finsync.auth.JellyfinRepository
import com.jpd.finsync.auth.Result
import com.jpd.finsync.model.ServerConfig
import kotlinx.coroutines.launch

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = JellyfinRepository(app)

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    sealed class LoginState {
        object Idle    : LoginState()
        object Loading : LoginState()
        data class Success(val config: ServerConfig) : LoginState()
        data class Error(val message: String)         : LoginState()
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
                is Result.Success -> _loginState.postValue(LoginState.Success(result.data))
                is Result.Error   -> _loginState.postValue(LoginState.Error(result.message))
            }
        }
    }

    fun checkExistingLogin(): ServerConfig? = repo.getSavedConfig()

    private fun normalizeUrl(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u.trimEnd('/')
    }
}
