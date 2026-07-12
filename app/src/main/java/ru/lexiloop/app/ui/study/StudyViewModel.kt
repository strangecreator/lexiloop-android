package ru.lexiloop.app.ui.study

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.JudgeResponse
import ru.lexiloop.app.data.api.NextCardResponse
import ru.lexiloop.app.data.api.ReviewRequest
import ru.lexiloop.app.data.api.ReviewResponse
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.ContentRepository
import ru.lexiloop.app.data.repo.PoolSelection
import ru.lexiloop.app.data.repo.PronunciationPlayer
import javax.inject.Inject

enum class StudyPhase { Answering, Checking, Feedback }

data class StudyUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val practiceMode: Boolean = false,
    val round: NextCardResponse? = null,
    val answer: String = "",
    val phase: StudyPhase = StudyPhase.Answering,
    val judge: JudgeResponse? = null,
    val review: ReviewResponse? = null,
    val revealed: Boolean = false,
    val poolName: String? = null,
) {
    val emptyMessage: String?
        get() = round?.takeIf { it.card == null }?.message
}

@HiltViewModel
class StudyViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val poolSelection: PoolSelection,
    private val player: PronunciationPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state

    private var practiceExcluded = mutableListOf<Int>()
    private var shownAt = 0L
    private var selectedPoolId: Int? = null
    private var initialized = false

    init {
        viewModelScope.launch {
            poolSelection.selected.collect { pool ->
                val changed = !initialized || pool?.id != selectedPoolId
                initialized = true
                selectedPoolId = pool?.id
                _state.update { it.copy(poolName = pool?.name) }
                if (changed) {
                    practiceExcluded = mutableListOf()
                    loadNext()
                }
            }
        }
    }

    fun setPracticeMode(enabled: Boolean) {
        if (_state.value.practiceMode == enabled) return
        practiceExcluded = mutableListOf()
        _state.update { it.copy(practiceMode = enabled) }
        loadNext()
    }

    fun onAnswerChange(value: String) {
        _state.update { it.copy(answer = value, error = null) }
    }

    fun loadNext() {
        _state.update {
            it.copy(
                loading = true, error = null, answer = "",
                phase = StudyPhase.Answering, judge = null, review = null, revealed = false,
            )
        }
        viewModelScope.launch {
            val mode = if (_state.value.practiceMode) "practice" else "due"
            when (val result = repository.nextCard(selectedPoolId, mode, practiceExcluded)) {
                is ApiResult.Success -> {
                    shownAt = SystemClock.elapsedRealtime()
                    _state.update { it.copy(loading = false, round = result.data) }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    /** Grades the typed answer, then records the review immediately. */
    fun checkAnswer() {
        val current = _state.value
        val round = current.round ?: return
        val card = round.card ?: return
        val direction = round.direction ?: return
        val answer = current.answer.trim()
        if (answer.isEmpty() || current.phase != StudyPhase.Answering) return

        _state.update { it.copy(phase = StudyPhase.Checking, error = null) }
        viewModelScope.launch {
            val judged = when (val result = repository.judge(card.id, answer, direction)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> {
                    _state.update {
                        it.copy(phase = StudyPhase.Answering, error = result.message)
                    }
                    return@launch
                }
            }
            val review = ReviewRequest(
                direction = direction,
                answer = answer,
                accepted = judged.accepted,
                judgeScore = judged.score.takeIf { it > 0 },
                judgeVerdict = judged.verdict,
                feedback = judged.feedback,
                responseMs = SystemClock.elapsedRealtime() - shownAt,
                practice = current.practiceMode,
            )
            recordReview(card.id, review, judged)
        }
    }

    /** Gives up on the card: reveals it and records a failed review. */
    fun showAnswer() {
        val current = _state.value
        val round = current.round ?: return
        val card = round.card ?: return
        val direction = round.direction ?: return
        if (current.phase == StudyPhase.Checking) return

        _state.update { it.copy(phase = StudyPhase.Checking, error = null) }
        viewModelScope.launch {
            val review = ReviewRequest(
                direction = direction,
                answer = current.answer.trim(),
                accepted = false,
                responseMs = SystemClock.elapsedRealtime() - shownAt,
                practice = current.practiceMode,
            )
            recordReview(card.id, review, judged = null)
        }
    }

    private suspend fun recordReview(cardId: Int, request: ReviewRequest, judged: JudgeResponse?) {
        when (val result = repository.review(cardId, request)) {
            is ApiResult.Success -> {
                if (_state.value.practiceMode) practiceExcluded.add(cardId)
                _state.update {
                    it.copy(
                        phase = StudyPhase.Feedback,
                        judge = judged,
                        review = result.data,
                        revealed = true,
                    )
                }
            }
            is ApiResult.Error -> _state.update {
                it.copy(phase = StudyPhase.Answering, error = result.message)
            }
        }
    }

    fun pronounce() {
        val term = _state.value.round?.card?.term ?: return
        player.play(term) {
            _state.update { it.copy(error = "Pronunciation is unavailable right now.") }
        }
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }
}
