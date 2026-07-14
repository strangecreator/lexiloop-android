package ru.lexiloop.app.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.OverviewResponse
import ru.lexiloop.app.data.api.PoolDto
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.ContentRepository
import ru.lexiloop.app.data.repo.PoolStore
import ru.lexiloop.app.data.repo.ToastBus
import javax.inject.Inject

data class OverviewUiState(
    val loading: Boolean = true,
    val stats: OverviewResponse? = null,
)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val poolStore: PoolStore,
    private val toastBus: ToastBus,
) : ViewModel() {

    val pools: StateFlow<List<PoolDto>> = poolStore.pools

    private val _state = MutableStateFlow(OverviewUiState())
    val state: StateFlow<OverviewUiState> = _state

    private var refreshJob: kotlinx.coroutines.Job? = null

    /**
     * Called on every visit to the page (like the site refetching on mount).
     * Transient network failures retry with a short backoff — the very first
     * fetch after install can race the device's app-verification window.
     */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            var attempts = 3
            while (attempts > 0) {
                attempts--
                when (val result = repository.overview()) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(loading = false, stats = result.data) }
                        attempts = 0
                    }
                    is ApiResult.Error -> {
                        if (result.code == null && attempts > 0) {
                            kotlinx.coroutines.delay(1500)
                            continue
                        }
                        _state.update { it.copy(loading = false) }
                        toastBus.error(result.message)
                        attempts = 0
                    }
                }
            }
            when (val pools = repository.pools()) {
                is ApiResult.Success -> poolStore.setPools(pools.data)
                is ApiResult.Error -> Unit // the me/pools shell load already reported
            }
        }
    }

    fun selectPool(id: Int) {
        poolStore.select(id)
    }
}
