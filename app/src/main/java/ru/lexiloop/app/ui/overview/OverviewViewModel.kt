package ru.lexiloop.app.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.OverviewResponse
import ru.lexiloop.app.data.api.PoolDto
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.ContentRepository
import ru.lexiloop.app.data.repo.PoolSelection
import javax.inject.Inject

data class OverviewUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val stats: OverviewResponse? = null,
    val pools: List<PoolDto> = emptyList(),
    val selectedPoolId: Int? = null,
)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val poolSelection: PoolSelection,
) : ViewModel() {

    private val _state = MutableStateFlow(OverviewUiState())
    val state: StateFlow<OverviewUiState> = _state

    init {
        refresh()
        viewModelScope.launch {
            poolSelection.selected.collect { pool ->
                _state.update { it.copy(selectedPoolId = pool?.id) }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val statsDeferred = async { repository.overview() }
            val poolsDeferred = async { repository.pools() }
            val stats = statsDeferred.await()
            val pools = poolsDeferred.await()
            _state.update { current ->
                var next = current.copy(loading = false, refreshing = false)
                when (stats) {
                    is ApiResult.Success -> next = next.copy(stats = stats.data)
                    is ApiResult.Error -> next = next.copy(error = stats.message)
                }
                when (pools) {
                    is ApiResult.Success -> next = next.copy(pools = pools.data)
                    is ApiResult.Error -> if (next.error == null) {
                        next = next.copy(error = pools.message)
                    }
                }
                next
            }
        }
    }

    fun selectPool(pool: PoolDto?) {
        poolSelection.select(pool)
    }
}
