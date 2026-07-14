package ru.lexiloop.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.AuthRepository
import javax.inject.Inject

data class AuthUiState(
    val registerMode: Boolean = false,
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !loading && username.isNotBlank() && password.isNotEmpty()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun toggleMode() = _state.update { it.copy(registerMode = !it.registerMode, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        if (current.registerMode) {
            // Mirror the site's form constraints with visible messages.
            val problem = when {
                current.username.trim().length < 3 -> "Username must be at least 3 characters."
                current.password.length < 8 -> "Password must be at least 8 characters."
                else -> null
            }
            if (problem != null) {
                _state.update { it.copy(error = problem) }
                return
            }
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = if (current.registerMode) {
                authRepository.register(current.username.trim(), current.password)
            } else {
                authRepository.login(current.username.trim(), current.password)
            }
            when (result) {
                // On success SessionManager flips the app to the home screen.
                is ApiResult.Success -> _state.update { it.copy(loading = false) }
                is ApiResult.Error -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }
}
