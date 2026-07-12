package ru.lexiloop.app.ui.study

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.api.JudgeRequest
import ru.lexiloop.app.data.api.JudgeResponse
import ru.lexiloop.app.data.api.NextCardResponse
import ru.lexiloop.app.data.api.ReviewRequest
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.ContentRepository
import ru.lexiloop.app.data.repo.PoolStore
import ru.lexiloop.app.data.repo.PronunciationPlayer
import ru.lexiloop.app.data.repo.ToastBus
import javax.inject.Inject

data class StudyUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val practiceMode: Boolean = false,
    val session: NextCardResponse? = null,
    val answer: String = "",
    val judge: JudgeResponse? = null,
    val revealed: Boolean = false,
    val reviewed: Boolean = false,
    val hintLetters: Int = 0,
    val roundCompleted: Int = 0,
    val roundTotal: Int = 0,
    val responseMs: Long? = null,
) {
    val card: FlashcardDto? get() = session?.card
    val direction: String get() = session?.direction ?: "term_to_definition"
    val showsTerm: Boolean get() = direction != "definition_to_term"

    /** queue_count counted the on-screen card; after saving it is one stale. */
    val remaining: Int
        get() = maxOf(0, (session?.queueCount ?: 0) - if (reviewed) 1 else 0)

    val progressTotal: Int get() = maxOf(roundTotal, roundCompleted + remaining)
    val progress: Float
        get() = if (progressTotal > 0) roundCompleted.toFloat() / progressTotal else 0f
}

/** Letter-hint helpers, ported from the web app. */
object Recall {
    fun base(term: String): String = term.replace(Regex("^to\\s+", RegexOption.IGNORE_CASE), "").trim()

    fun letterCount(term: String): Int = base(term).count { it.isLetter() }

    fun maskedTerm(term: String, revealed: Int): String {
        var remaining = revealed
        return base(term).map { ch ->
            when {
                !ch.isLetter() -> ch
                remaining > 0 -> { remaining -= 1; ch }
                else -> '_'
            }
        }.joinToString(" ")
    }

    /** Replaces the term (and simple inflections) with blanks in example text. */
    fun maskAnswer(text: String, card: FlashcardDto): String {
        val baseTerm = base(card.term)
        val candidates = mutableSetOf(card.term, baseTerm)
        candidates.addAll(card.aliases)
        candidates.addAll(card.formEntries().map { it.second })
        if (baseTerm.isNotEmpty() && !baseTerm.contains(' ')) {
            candidates.add("${baseTerm}s"); candidates.add("${baseTerm}es")
            candidates.add("${baseTerm}ed"); candidates.add("${baseTerm}ing")
            if (baseTerm.endsWith('e')) {
                candidates.add("${baseTerm}d")
                candidates.add(baseTerm.dropLast(1) + "ing")
            }
            if (baseTerm.endsWith('y')) {
                candidates.add(baseTerm.dropLast(1) + "ies")
                candidates.add(baseTerm.dropLast(1) + "ied")
            }
        }
        var result = text
        candidates.filter { it.isNotBlank() }.sortedByDescending { it.length }.forEach { value ->
            result = result.replace(Regex("\\b${Regex.escape(value)}\\b", RegexOption.IGNORE_CASE), "_____")
        }
        return result
    }
}

@HiltViewModel
class StudyViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val poolStore: PoolStore,
    private val player: PronunciationPlayer,
    private val toastBus: ToastBus,
) : ViewModel() {

    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state

    val activePoolName: String? get() = poolStore.activePool?.name

    private var practiceSeen = mutableListOf<Int>()
    private var shownAt = 0L
    private var observedPoolId: Int? = null
    private var poolInitialized = false

    init {
        viewModelScope.launch {
            poolStore.activePoolId.collect { poolId ->
                val changed = !poolInitialized || poolId != observedPoolId
                poolInitialized = true
                observedPoolId = poolId
                if (changed) {
                    practiceSeen = mutableListOf()
                    load(due = true, resetRound = true)
                }
            }
        }
    }

    private fun load(due: Boolean, resetRound: Boolean = false, adjustedTotal: Int? = null) {
        val mode = if (due) "due" else "practice"
        _state.update {
            it.copy(
                loading = true, practiceMode = !due, answer = "", judge = null,
                revealed = false, reviewed = false, hintLetters = 0, responseMs = null,
            )
        }
        viewModelScope.launch {
            val excluded = if (due) emptyList() else practiceSeen.toList()
            when (val result = repository.nextCard(observedPoolId, mode, excluded)) {
                is ApiResult.Success -> {
                    shownAt = SystemClock.elapsedRealtime()
                    val data = result.data
                    _state.update { current ->
                        val completed: Int
                        val total: Int
                        if (!due) {
                            completed = data.roundCompleted.takeIf { it > 0 } ?: excluded.size
                            total = data.roundTotal.takeIf { it > 0 }
                                ?: maxOf(excluded.size + data.queueCount, 0)
                        } else {
                            completed = if (resetRound) 0 else current.roundCompleted
                            total = when {
                                adjustedTotal != null -> maxOf(adjustedTotal, completed + data.queueCount)
                                resetRound -> data.queueCount
                                else -> maxOf(current.roundTotal, completed + data.queueCount)
                            }
                        }
                        current.copy(
                            loading = false,
                            session = data,
                            roundCompleted = completed,
                            roundTotal = total,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(loading = false) }
                    toastBus.error(result.message)
                }
            }
        }
    }

    fun onAnswerChange(value: String) = _state.update { it.copy(answer = value) }

    fun revealHintLetter() {
        val card = _state.value.card ?: return
        _state.update {
            it.copy(hintLetters = minOf(Recall.letterCount(card.term), it.hintLetters + 1))
        }
    }

    private fun elapsed(): Long = maxOf(0, SystemClock.elapsedRealtime() - shownAt)

    /** `Check answer`: the judge endpoint also records the review server-side. */
    fun checkAnswer() {
        val current = _state.value
        val card = current.card ?: return
        if (current.busy || current.answer.isBlank() || current.judge != null) return
        val measured = current.responseMs ?: elapsed()
        _state.update { it.copy(busy = true, responseMs = measured) }
        viewModelScope.launch {
            val request = JudgeRequest(
                answer = current.answer,
                direction = current.direction,
                responseMs = measured,
                practice = current.practiceMode,
                hintRevealedLetters = current.hintLetters,
                hintTotalLetters = Recall.letterCount(card.term),
            )
            when (val result = repository.judge(card.id, request)) {
                is ApiResult.Success -> {
                    val judged = result.data
                    if (judged.reviewRecorded) {
                        markReviewed(card.id)
                        _state.update { it.copy(busy = false, judge = judged) }
                    } else {
                        // Fallback for a lost lock race: record explicitly.
                        val saved = recordReview(card, judged, measured)
                        _state.update { it.copy(busy = false, judge = if (saved) judged else null) }
                    }
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(busy = false) }
                    toastBus.error(result.message)
                }
            }
        }
    }

    /** `Show answer`: reveal and save a failed review immediately. */
    fun showAnswer() {
        val current = _state.value
        val card = current.card ?: return
        if (current.busy || current.revealed) return
        val measured = current.responseMs ?: elapsed()
        _state.update { it.copy(revealed = true, responseMs = measured, busy = true) }
        viewModelScope.launch {
            recordReview(card, judged = null, measured)
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun recordReview(card: FlashcardDto, judged: JudgeResponse?, measured: Long): Boolean {
        if (_state.value.reviewed) return true
        val current = _state.value
        val request = ReviewRequest(
            direction = current.direction,
            answer = current.answer,
            accepted = judged?.accepted ?: false,
            judgeScore = judged?.score?.takeIf { it > 0 },
            judgeVerdict = judged?.verdict.orEmpty(),
            feedback = judged?.feedback.orEmpty(),
            responseMs = measured,
            practice = current.practiceMode,
            hintRevealedLetters = current.hintLetters,
            hintTotalLetters = Recall.letterCount(card.term),
        )
        var result = repository.review(card.id, request)
        if (result is ApiResult.Error && result.code == 409) {
            delay(850)
            result = repository.review(card.id, request)
        }
        return when (result) {
            is ApiResult.Success -> {
                markReviewed(card.id)
                true
            }
            is ApiResult.Error -> {
                toastBus.error(result.message)
                false
            }
        }
    }

    private fun markReviewed(cardId: Int) {
        val current = _state.value
        if (current.practiceMode) {
            if (!practiceSeen.contains(cardId)) {
                practiceSeen.add(cardId)
                _state.update { it.copy(reviewed = true, roundCompleted = it.roundCompleted + 1) }
                return
            }
            _state.update { it.copy(reviewed = true) }
        } else {
            _state.update { it.copy(reviewed = true, roundCompleted = it.roundCompleted + 1) }
        }
    }

    fun next() {
        val current = _state.value
        val card = current.card ?: return
        if (current.busy || (current.judge == null && !current.revealed)) return
        viewModelScope.launch {
            if (!current.reviewed) {
                val saved = recordReview(card, current.judge, current.responseMs ?: elapsed())
                if (!saved) return@launch
            }
            load(due = !current.practiceMode)
        }
    }

    /** ⌘− on the web: blocks the card from future study and advances. */
    fun suspendCurrent() {
        val current = _state.value
        val card = current.card ?: return
        if (current.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = repository.suspendCard(card.id)) {
                is ApiResult.Success -> {
                    toastBus.success("Blocked “${card.term}” from future study")
                    if (current.practiceMode) {
                        if (!practiceSeen.contains(card.id)) practiceSeen.add(card.id)
                        load(due = false)
                    } else {
                        load(
                            due = true,
                            adjustedTotal = maxOf(current.roundCompleted, current.roundTotal - 1),
                        )
                    }
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(busy = false) }
                    toastBus.error(result.message)
                }
            }
        }
    }

    fun startPractice() {
        practiceSeen = mutableListOf()
        load(due = false)
    }

    fun returnToDue() {
        practiceSeen = mutableListOf()
        load(due = true, resetRound = true)
    }

    fun reload() = load(due = !_state.value.practiceMode, resetRound = true)

    fun pronounce(text: String) {
        player.play(text) { toastBus.error("Pronunciation is unavailable right now.") }
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }
}
