package ru.lexiloop.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.MeResponse
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.AuthRepository
import ru.lexiloop.app.data.repo.ContentRepository
import javax.inject.Inject

data class SettingsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val me: MeResponse? = null,
    val loggingOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = contentRepository.me()) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, me = result.data)
                }
                is ApiResult.Error -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun logout() {
        _state.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            authRepository.logout()
            // SessionManager flips the app back to the auth screen.
        }
    }
}
