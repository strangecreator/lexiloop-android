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

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = repository.overview()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, stats = result.data) }
                is ApiResult.Error -> {
                    _state.update { it.copy(loading = false) }
                    toastBus.error(result.message)
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
