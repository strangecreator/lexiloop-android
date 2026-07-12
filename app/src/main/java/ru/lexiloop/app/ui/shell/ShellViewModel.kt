package ru.lexiloop.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.PoolDto
import ru.lexiloop.app.data.api.SettingsDto
import ru.lexiloop.app.data.auth.SessionManager
import ru.lexiloop.app.data.auth.SessionState
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.AuthRepository
import ru.lexiloop.app.data.repo.ContentRepository
import ru.lexiloop.app.data.repo.PoolStore
import ru.lexiloop.app.data.repo.SettingsStore
import ru.lexiloop.app.data.repo.ToastBus
import ru.lexiloop.app.data.repo.ToastEvent
import javax.inject.Inject

/** The web app's pages, driven by the sidebar exactly like on the site. */
enum class LexiPage(val title: String) {
    Home("Overview"),
    Study("Study"),
    Library("Library"),
    Analytics("AI usage"),
    Settings("Settings"),
}

data class ShellUiState(
    val username: String = "",
    val shellLoaded: Boolean = false,
)

@HiltViewModel
class ShellViewModel @Inject constructor(
    val repository: ContentRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    val poolStore: PoolStore,
    val settingsStore: SettingsStore,
    val toastBus: ToastBus,
) : ViewModel() {

    val session: StateFlow<SessionState> = sessionManager.state
    val settings: StateFlow<SettingsDto> = settingsStore.settings
    val pools: StateFlow<List<PoolDto>> = poolStore.pools
    val activePoolId: StateFlow<Int?> = poolStore.activePoolId

    private val _page = MutableStateFlow(LexiPage.Home)
    val page: StateFlow<LexiPage> = _page

    private val _state = MutableStateFlow(ShellUiState())
    val state: StateFlow<ShellUiState> = _state

    private val _toast = MutableStateFlow<ToastEvent?>(null)
    val toast: StateFlow<ToastEvent?> = _toast

    init {
        viewModelScope.launch {
            session.collect { if (it is SessionState.LoggedIn) loadShell() }
        }
        viewModelScope.launch {
            toastBus.events.collect { event ->
                _toast.value = event
                kotlinx.coroutines.delay(3800)
                if (_toast.value === event) _toast.value = null
            }
        }
    }

    fun navigate(page: LexiPage) {
        _page.value = page
    }

    fun selectPool(id: Int?) {
        poolStore.select(id)
    }

    fun loadShell() {
        viewModelScope.launch { refreshShell() }
    }

    suspend fun refreshShell() {
        coroutineScope {
            val me = async { repository.me() }
            val pools = async { repository.pools() }
            when (val result = me.await()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(username = result.data.username, shellLoaded = true) }
                    result.data.settings?.let(settingsStore::update)
                }
                is ApiResult.Error -> {
                    if (result.code == 401) sessionManager.clear() else toastBus.error(result.message)
                }
            }
            when (val result = pools.await()) {
                is ApiResult.Success -> poolStore.setPools(result.data)
                is ApiResult.Error -> if (result.code != 401) toastBus.error(result.message)
            }
        }
    }

    /** The sidebar theme switch: flips dark ↔ light and persists server-side. */
    fun toggleTheme(systemDark: Boolean) {
        val current = settings.value.theme
        val effectiveDark = when (current) {
            "dark" -> true
            "light" -> false
            else -> systemDark
        }
        val next = if (effectiveDark) "light" else "dark"
        viewModelScope.launch {
            when (val result = repository.patchTheme(next)) {
                is ApiResult.Success -> settingsStore.update(result.data)
                is ApiResult.Error -> toastBus.error(result.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.logout()
            _page.value = LexiPage.Home
            _state.value = ShellUiState()
        }
    }
}
