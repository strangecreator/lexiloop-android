package ru.lexiloop.app.data.repo

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import ru.lexiloop.app.data.api.PoolDto
import ru.lexiloop.app.data.api.SettingsDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pools plus the active pool, shared by the sidebar, Overview, Study, and
 * Library — mirrors the web app's `pools`/`activePool` shell state.
 */
@Singleton
class PoolStore @Inject constructor() {
    private val _pools = MutableStateFlow<List<PoolDto>>(emptyList())
    val pools: StateFlow<List<PoolDto>> = _pools

    private val _activePoolId = MutableStateFlow<Int?>(null)
    val activePoolId: StateFlow<Int?> = _activePoolId

    val activePool: PoolDto?
        get() = _pools.value.firstOrNull { it.id == _activePoolId.value }

    fun setPools(pools: List<PoolDto>) {
        _pools.value = pools
        // Keep the selection valid; default to the first pool like the web app.
        val current = _activePoolId.value
        if (current == null || pools.none { it.id == current }) {
            _activePoolId.value = pools.firstOrNull()?.id
        }
    }

    fun select(id: Int?) {
        _activePoolId.value = id
    }
}

/** Server-driven appearance and study preferences, shared across screens. */
@Singleton
class SettingsStore @Inject constructor() {
    private val _settings = MutableStateFlow(SettingsDto())
    val settings: StateFlow<SettingsDto> = _settings

    fun update(settings: SettingsDto) {
        _settings.value = settings
    }
}

data class ToastEvent(val message: String, val isError: Boolean = false)

/** The web app's bottom toast, as an app-wide event bus. */
@Singleton
class ToastBus @Inject constructor() {
    private val _events = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ToastEvent> = _events

    fun success(message: String) {
        _events.tryEmit(ToastEvent(message, isError = false))
    }

    fun error(message: String) {
        _events.tryEmit(ToastEvent(message, isError = true))
    }
}
