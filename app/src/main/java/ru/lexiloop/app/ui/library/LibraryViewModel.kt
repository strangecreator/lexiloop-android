package ru.lexiloop.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.BulkJob
import ru.lexiloop.app.data.api.CardWriteBody
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.api.NormalizeResponse
import ru.lexiloop.app.data.api.PoolDto
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.ContentRepository
import ru.lexiloop.app.data.repo.PoolStore
import ru.lexiloop.app.data.repo.ToastBus
import javax.inject.Inject

private const val PAGE_SIZE = 30

data class LibraryUiState(
    val loading: Boolean = true,
    val generating: Boolean = false,
    val term: String = "",
    val search: String = "",
    val cards: List<FlashcardDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val expandedCardId: Int? = null,
    val busyCardId: Int? = null,
    val imageBusyCardId: Int? = null,
) {
    val totalPages: Int get() = maxOf(1, (total + PAGE_SIZE - 1) / PAGE_SIZE)
    val firstIndex: Int get() = if (total == 0) 0 else (page - 1) * PAGE_SIZE + 1
    val lastIndex: Int get() = minOf(total, page * PAGE_SIZE)
}

data class BulkUiState(
    val open: Boolean = false,
    val terms: String = "",
    val preview: NormalizeResponse? = null,
    val batchSize: Int = 20,
    val job: BulkJob? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val poolStore: PoolStore,
    private val player: ru.lexiloop.app.data.repo.PronunciationPlayer,
    val toastBus: ToastBus,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    val pools: StateFlow<List<PoolDto>> = poolStore.pools
    val activePoolId: StateFlow<Int?> = poolStore.activePoolId

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    private val _bulk = MutableStateFlow(BulkUiState())
    val bulk: StateFlow<BulkUiState> = _bulk

    private val searchFlow = MutableStateFlow("")
    private var loadJob: Job? = null
    private var bulkPollJob: Job? = null

    init {
        viewModelScope.launch {
            poolStore.activePoolId.collect {
                _state.update { current -> current.copy(page = 1, expandedCardId = null) }
                reload()
            }
        }
        viewModelScope.launch {
            searchFlow.drop(1).debounce(250).distinctUntilChanged().collect {
                _state.update { current -> current.copy(page = 1) }
                reload()
            }
        }
    }

    val activePool: PoolDto? get() = poolStore.activePool

    // The server accepts free-form generation requests up to 1000 characters.
    fun onTermChange(value: String) = _state.update { it.copy(term = value.take(1000)) }

    fun onSearchChange(value: String) {
        _state.update { it.copy(search = value) }
        searchFlow.value = value.trim()
    }

    fun toggleExpanded(cardId: Int) = _state.update {
        it.copy(expandedCardId = if (it.expandedCardId == cardId) null else cardId)
    }

    fun setPage(page: Int) {
        val clamped = page.coerceIn(1, _state.value.totalPages)
        if (clamped == _state.value.page) return
        _state.update { it.copy(page = clamped, expandedCardId = null) }
        reload()
    }

    fun reload() {
        val poolId = activePoolId.value
        loadJob?.cancel()
        if (poolId == null) {
            _state.update { it.copy(loading = false, cards = emptyList(), total = 0) }
            return
        }
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val current = _state.value
            when (val result = repository.flashcards(poolId, current.search.trim(), current.page)) {
                is ApiResult.Success -> {
                    // If the page emptied out (deletions), fall back to the last page.
                    if (result.data.results.isEmpty() && result.data.count > 0 && current.page > 1) {
                        _state.update {
                            it.copy(page = maxOf(1, (result.data.count + PAGE_SIZE - 1) / PAGE_SIZE))
                        }
                        reload()
                        return@launch
                    }
                    _state.update {
                        it.copy(loading = false, cards = result.data.results, total = result.data.count)
                    }
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(loading = false) }
                    toastBus.error(result.message)
                }
            }
        }
    }

    fun generate() {
        val poolId = activePoolId.value ?: return
        val term = _state.value.term.trim()
        if (term.isEmpty() || _state.value.generating) return
        _state.update { it.copy(generating = true) }
        viewModelScope.launch {
            when (val result = repository.generate(poolId, term)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(generating = false, term = "", search = "", page = 1) }
                    searchFlow.value = ""
                    toastBus.success("Generated “${result.data.term}”")
                    reload()
                    refreshPools()
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(generating = false) }
                    toastBus.error(result.message)
                }
            }
        }
    }

    fun saveCard(cardId: Int?, body: CardWriteBody, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val result = if (cardId == null) repository.createCard(body) else repository.patchCard(cardId, body)
            when (result) {
                is ApiResult.Success -> {
                    toastBus.success(if (cardId == null) "Card created" else "Card updated")
                    reload()
                    refreshPools()
                    onDone(null)
                }
                is ApiResult.Error -> onDone(result.message)
            }
        }
    }

    fun unsuspendCard(card: FlashcardDto) = cardAction(card.id) {
        repository.unsuspendCard(card.id).also {
            if (it is ApiResult.Success) toastBus.success("Unblocked “${card.term}”")
        }
    }

    fun suspendCardAction(card: FlashcardDto) = cardAction(card.id) {
        repository.suspendCard(card.id).also {
            if (it is ApiResult.Success) toastBus.success("Blocked “${card.term}”")
        }
    }

    fun delete(card: FlashcardDto) {
        _state.update { it.copy(busyCardId = card.id) }
        viewModelScope.launch {
            when (val result = repository.deleteCard(card.id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(busyCardId = null, expandedCardId = null) }
                    reload()
                    refreshPools()
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(busyCardId = null) }
                    toastBus.error(result.message)
                }
            }
        }
    }

    // --- Card images (mirrors the site's CardImageControls) ---

    fun setImageFromLink(card: FlashcardDto, url: String) {
        val link = url.trim()
        if (link.isEmpty() || _state.value.imageBusyCardId != null) return
        _state.update { it.copy(imageBusyCardId = card.id) }
        viewModelScope.launch {
            finishImage(repository.setCardImageFromLink(card.id, link), "Image saved")
        }
    }

    fun uploadImage(card: FlashcardDto, uri: android.net.Uri) {
        if (_state.value.imageBusyCardId != null) return
        _state.update { it.copy(imageBusyCardId = card.id) }
        viewModelScope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                _state.update { it.copy(imageBusyCardId = null) }
                toastBus.error("Could not read that image.")
                return@launch
            }
            finishImage(repository.uploadCardImage(card.id, bytes, "upload.jpg"), "Image saved")
        }
    }

    fun removeImage(card: FlashcardDto) {
        if (_state.value.imageBusyCardId != null) return
        _state.update { it.copy(imageBusyCardId = card.id) }
        viewModelScope.launch {
            finishImage(repository.removeCardImage(card.id), "Image removed")
        }
    }

    private fun finishImage(result: ApiResult<FlashcardDto>, successMessage: String) {
        _state.update { it.copy(imageBusyCardId = null) }
        when (result) {
            is ApiResult.Success -> {
                updateCardInPlace(result.data)
                toastBus.success(successMessage)
            }
            is ApiResult.Error -> toastBus.error(result.message)
        }
    }

    fun pronounce(term: String) {
        player.play(term) { toastBus.error("Pronunciation is unavailable right now.") }
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }

    private fun cardAction(cardId: Int, action: suspend () -> ApiResult<FlashcardDto>) {
        _state.update { it.copy(busyCardId = cardId) }
        viewModelScope.launch {
            when (val result = action()) {
                is ApiResult.Success -> _state.update { current ->
                    current.copy(
                        busyCardId = null,
                        cards = current.cards.map { if (it.id == cardId) result.data else it },
                    )
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(busyCardId = null) }
                    toastBus.error(result.message)
                }
            }
        }
    }

    fun updateCardInPlace(card: FlashcardDto) = _state.update { current ->
        current.copy(cards = current.cards.map { if (it.id == card.id) card else it })
    }

    private fun refreshPools() {
        viewModelScope.launch {
            when (val result = repository.pools()) {
                is ApiResult.Success -> poolStore.setPools(result.data)
                is ApiResult.Error -> Unit
            }
        }
    }

    // --- Bulk generation ---

    fun openBulk() {
        val poolId = activePoolId.value ?: return
        _bulk.value = BulkUiState(open = true)
        // Resume an active job if the worker is still running one for this pool.
        viewModelScope.launch {
            when (val result = repository.bulkJobs(poolId)) {
                is ApiResult.Success -> {
                    val active = result.data.firstOrNull { it.isActive }
                    if (active != null) {
                        _bulk.update { it.copy(job = active) }
                        pollBulk(active.id)
                    }
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun closeBulk() {
        bulkPollJob?.cancel()
        val job = _bulk.value.job
        _bulk.value = BulkUiState()
        if (job != null && !job.isActive) {
            reload()
            refreshPools()
        }
    }

    fun onBulkTermsChange(value: String) = _bulk.update { it.copy(terms = value, error = null) }

    fun onBulkBatchSize(value: Int) = _bulk.update { it.copy(batchSize = value.coerceIn(1, 200)) }

    fun bulkBack() = _bulk.update { it.copy(preview = null, error = null) }

    fun normalize() {
        val terms = _bulk.value.terms
        if (terms.isBlank() || _bulk.value.busy) return
        _bulk.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.normalizeTerms(terms)) {
                is ApiResult.Success -> _bulk.update {
                    it.copy(
                        busy = false,
                        preview = result.data,
                        batchSize = minOf(20, maxOf(1, result.data.normalized.size)),
                    )
                }
                is ApiResult.Error -> _bulk.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun startBulk() {
        val poolId = activePoolId.value ?: return
        val preview = _bulk.value.preview ?: return
        if (preview.normalized.isEmpty() || _bulk.value.busy) return
        _bulk.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.startBulkJob(poolId, preview.normalized, _bulk.value.batchSize)) {
                is ApiResult.Success -> {
                    _bulk.update { it.copy(busy = false, job = result.data) }
                    pollBulk(result.data.id)
                }
                is ApiResult.Error -> _bulk.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun cancelBulk() {
        val job = _bulk.value.job ?: return
        viewModelScope.launch {
            when (val result = repository.cancelBulkJob(job.id)) {
                is ApiResult.Success -> _bulk.update { it.copy(job = result.data) }
                is ApiResult.Error -> _bulk.update { it.copy(error = result.message) }
            }
        }
    }

    private fun pollBulk(jobId: String) {
        bulkPollJob?.cancel()
        bulkPollJob = viewModelScope.launch {
            while (true) {
                delay(800)
                when (val result = repository.bulkJob(jobId)) {
                    is ApiResult.Success -> {
                        _bulk.update { it.copy(job = result.data) }
                        if (!result.data.isActive) break
                    }
                    is ApiResult.Error -> {
                        _bulk.update { it.copy(error = result.message) }
                        break
                    }
                }
            }
        }
    }
}
