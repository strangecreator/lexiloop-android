package ru.lexiloop.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.ContentRepository
import ru.lexiloop.app.data.repo.PoolSelection
import javax.inject.Inject

data class LibraryUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val cards: List<FlashcardDto> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val poolName: String? = null,
    val expandedCardId: Int? = null,
    val busyCardId: Int? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val poolSelection: PoolSelection,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    private val queryFlow = MutableStateFlow("")
    private var page = 1
    private var poolId: Int? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            poolSelection.selected.collect { pool ->
                poolId = pool?.id
                _state.update { it.copy(poolName = pool?.name) }
                reload()
            }
        }
        viewModelScope.launch {
            queryFlow
                .drop(1)
                .debounce(300)
                .distinctUntilChanged()
                .collect { reload() }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        queryFlow.value = value
    }

    fun toggleExpanded(cardId: Int) {
        _state.update {
            it.copy(expandedCardId = if (it.expandedCardId == cardId) null else cardId)
        }
    }

    fun reload() {
        page = 1
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch { fetch(reset = true) }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore) return
        page += 1
        _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch { fetch(reset = false) }
    }

    private suspend fun fetch(reset: Boolean) {
        val result = repository.flashcards(
            pool = poolId,
            search = _state.value.query,
            page = page,
        )
        when (result) {
            is ApiResult.Success -> _state.update { current ->
                val cards = if (reset) result.data.results else current.cards + result.data.results
                current.copy(
                    loading = false,
                    loadingMore = false,
                    cards = cards,
                    totalCount = result.data.count,
                    hasMore = result.data.next != null,
                )
            }
            is ApiResult.Error -> _state.update {
                it.copy(loading = false, loadingMore = false, error = result.message)
            }
        }
    }

    fun setSuspended(card: FlashcardDto, suspended: Boolean) {
        _state.update { it.copy(busyCardId = card.id) }
        viewModelScope.launch {
            val result = if (suspended) {
                repository.suspendCard(card.id)
            } else {
                repository.unsuspendCard(card.id)
            }
            when (result) {
                is ApiResult.Success -> _state.update { current ->
                    current.copy(
                        busyCardId = null,
                        cards = current.cards.map { if (it.id == card.id) result.data else it },
                    )
                }
                is ApiResult.Error -> _state.update {
                    it.copy(busyCardId = null, error = result.message)
                }
            }
        }
    }

    fun deleteCard(card: FlashcardDto) {
        _state.update { it.copy(busyCardId = card.id) }
        viewModelScope.launch {
            when (val result = repository.deleteCard(card.id)) {
                is ApiResult.Success -> _state.update { current ->
                    current.copy(
                        busyCardId = null,
                        expandedCardId = null,
                        totalCount = (current.totalCount - 1).coerceAtLeast(0),
                        cards = current.cards.filterNot { it.id == card.id },
                    )
                }
                is ApiResult.Error -> _state.update {
                    it.copy(busyCardId = null, error = result.message)
                }
            }
        }
    }
}
