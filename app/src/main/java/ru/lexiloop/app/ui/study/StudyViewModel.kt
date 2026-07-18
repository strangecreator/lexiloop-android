package ru.lexiloop.app.ui.study

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.api.JudgeRequest
import ru.lexiloop.app.data.api.JudgeResponse
import ru.lexiloop.app.data.api.NextCardResponse
import ru.lexiloop.app.data.api.ReviewRequest
import ru.lexiloop.app.data.offline.OfflineScheduler
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.CardImages
import ru.lexiloop.app.data.repo.ContentRepository
import ru.lexiloop.app.data.repo.EffectiveStudyPrefs
import ru.lexiloop.app.data.repo.PoolStore
import ru.lexiloop.app.data.repo.PronunciationPlayer
import ru.lexiloop.app.data.repo.ToastBus
import ru.lexiloop.app.data.repo.resolveStudyPrefs
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
    val offline: Boolean = false,
    val imageBusy: Boolean = false,
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

    /**
     * The masked term split into per-word chunks, so the hint block can wrap
     * long phrases at word boundaries instead of overflowing.
     */
    fun maskedWords(term: String, revealed: Int): List<String> {
        var remaining = revealed
        return base(term).split(' ').filter { it.isNotEmpty() }.map { word ->
            word.map { ch ->
                when {
                    !ch.isLetter() -> ch
                    remaining > 0 -> { remaining -= 1; ch }
                    else -> '_'
                }
            }.joinToString(" ")
        }
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
    private val offlineCache: ru.lexiloop.app.data.offline.OfflineCache,
    private val networkMonitor: ru.lexiloop.app.data.offline.NetworkMonitor,
    private val syncManager: ru.lexiloop.app.data.offline.SyncManager,
    private val settingsStore: ru.lexiloop.app.data.repo.SettingsStore,
    private val devicePrefs: ru.lexiloop.app.data.repo.DevicePrefs,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state

    /** Account settings with this device's overrides applied. */
    val studyPrefs: StateFlow<EffectiveStudyPrefs> =
        combine(settingsStore.settings, devicePrefs.overrides) { server, device ->
            resolveStudyPrefs(server, device)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            resolveStudyPrefs(settingsStore.settings.value, devicePrefs.overrides.value),
        )

    val activePoolName: String? get() = poolStore.activePool?.name

    private var practiceSeen = mutableListOf<Int>()
    private var shownAt = 0L
    private var observedPoolId: Int? = null
    private var poolInitialized = false
    private var firstShowConsumed = false

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
        // Reconnecting replays the offline reviews and refreshes the cache;
        // an offline session on screen is stale the moment that lands. Reload
        // it unless the user is mid-card, so the queue counts ("839 new")
        // snap to the server's truth without restarting the app.
        viewModelScope.launch {
            syncManager.completedSyncs.drop(1).collect {
                val current = _state.value
                val untouched = current.judge == null && !current.revealed &&
                    current.answer.isBlank() && !current.busy && !current.loading
                if (current.offline && untouched) {
                    practiceSeen = mutableListOf()
                    load(due = !current.practiceMode, resetRound = true)
                }
            }
        }
    }

    /**
     * The web StudyPage refetches /study/next/ on every mount, so settings
     * changed in between (task types, images) apply immediately. This view
     * model outlives the page, so re-entering the page starts a fresh due
     * round the same way. The first show rides on the load from `init`.
     */
    fun onPageShown() {
        if (!firstShowConsumed) {
            firstShowConsumed = true
            return
        }
        practiceSeen = mutableListOf()
        load(due = true, resetRound = true)
    }

    private fun load(due: Boolean, resetRound: Boolean = false, adjustedTotal: Int? = null) {
        clearPrefetchedSession()
        val mode = if (due) "due" else "practice"
        _state.update {
            it.copy(
                loading = true, practiceMode = !due, answer = "", judge = null,
                revealed = false, reviewed = false, hintLetters = 0, responseMs = null,
            )
        }
        viewModelScope.launch {
            val excluded = if (due) emptyList() else practiceSeen.toList()
            // Task types are device-local (default: recall only), so the
            // effective selection always rides along; the prefetch count is
            // sent only when this device overrides the account.
            val overrides = devicePrefs.overrides.value
            when (
                val result = repository.nextCard(
                    observedPoolId, mode, excluded,
                    prefetch = overrides.imagePrefetchCount,
                    directions = studyPrefs.value.studyDirections,
                )
            ) {
                is ApiResult.Success -> applySession(result.data, due, resetRound, adjustedTotal, offline = false)
                is ApiResult.Error -> {
                    val fallback = if (result.code == null) offlineSession(due, excluded) else null
                    if (fallback != null) {
                        applySession(fallback, due, resetRound, adjustedTotal, offline = true)
                    } else {
                        _state.update { it.copy(loading = false) }
                        toastBus.error(result.message)
                    }
                }
            }
        }
    }

    private fun applySession(
        data: NextCardResponse,
        due: Boolean,
        resetRound: Boolean,
        adjustedTotal: Int?,
        offline: Boolean,
    ) {
        shownAt = SystemClock.elapsedRealtime()
        prefetchUpcomingImages(data)
        _state.update { current ->
            val completed: Int
            val total: Int
            if (!due) {
                completed = data.roundCompleted.takeIf { it > 0 } ?: practiceSeen.size
                total = data.roundTotal.takeIf { it > 0 }
                    ?: maxOf(practiceSeen.size + data.queueCount, 0)
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
                offline = offline,
            )
        }
    }

    // --- Next-session prefetch: once the current card's review is saved, the
    // server's answer to "what comes next" is final, so it is fetched while
    // the user reads the feedback and "Next task" switches instantly. ---

    private var nextSession: NextCardResponse? = null
    private var nextSessionJob: kotlinx.coroutines.Job? = null

    private fun clearPrefetchedSession() {
        nextSessionJob?.cancel()
        nextSessionJob = null
        nextSession = null
    }

    private fun schedulePrefetchNextSession() {
        clearPrefetchedSession()
        val current = _state.value
        if (current.offline || !networkMonitor.online.value) return
        val due = !current.practiceMode
        val excluded = if (due) emptyList() else practiceSeen.toList()
        nextSessionJob = viewModelScope.launch {
            val overrides = devicePrefs.overrides.value
            val result = repository.nextCard(
                observedPoolId,
                if (due) "due" else "practice",
                excluded,
                prefetch = overrides.imagePrefetchCount,
                directions = studyPrefs.value.studyDirections,
            )
            if (result is ApiResult.Success) {
                nextSession = result.data
                warmSessionImages(result.data)
            }
        }
    }

    /** Warms the prefetched card's own image so its reveal starts instantly. */
    private fun warmSessionImages(data: NextCardResponse) {
        val card = data.card ?: return
        if (!card.hasImage) return
        val loader = appContext.imageLoader
        for (thumb in listOf(true, false)) {
            loader.enqueue(
                ImageRequest.Builder(appContext)
                    .data(CardImages.imageUrl(card.id, card.imageKey, thumb))
                    .build(),
            )
        }
    }

    /** Warms Coil's cache so the next cards' reveals start instantly. */
    private fun prefetchUpcomingImages(data: NextCardResponse) {
        val count = studyPrefs.value.imagePrefetchCount
        if (count <= 0) return
        val loader = appContext.imageLoader
        data.upcomingImages.take(count).forEach { upcoming ->
            for (thumb in listOf(true, false)) {
                loader.enqueue(
                    ImageRequest.Builder(appContext)
                        .data(CardImages.imageUrl(upcoming.id, upcoming.imageKey, thumb))
                        .build(),
                )
            }
        }
    }

    /**
     * Builds a Definition → word session from the local card cache using the
     * same queue rules as the server: cards reviewed offline are rescheduled
     * locally (so learning steps come back within the session instead of
     * disappearing), and the daily new-card limit is honored using the last
     * synced count of new cards introduced today plus the ones introduced
     * offline since.
     */
    private suspend fun offlineSession(due: Boolean, excluded: List<Int>): NextCardResponse? {
        val poolId = observedPoolId ?: return null
        val cards = offlineCache.loadCards(poolId) ?: return null
        val available = cards.filter { !it.suspended }
        val now = System.currentTimeMillis()

        fun state(card: FlashcardDto): String = card.schedule?.state ?: "new"

        fun dueAtMillis(card: FlashcardDto): Long? =
            OfflineScheduler.parseInstantMillis(card.schedule?.dueAt)

        val queue = if (due) {
            val dueCards = available.filter { card ->
                card.schedule == null || (dueAtMillis(card)?.let { it <= now } ?: true)
            }
            val scheduled = dueCards.filter { state(it) != "new" }
            val newLimit = offlineRemainingNewSlots(now)
            val cappedNew = dueCards.filter { state(it) == "new" }
                .sortedWith(compareBy({ dueAtMillis(it) ?: Long.MAX_VALUE }, { it.id }))
                .take(newLimit)
            (scheduled + cappedNew).sortedWith(
                compareByDescending<FlashcardDto> { OfflineScheduler.stateRank(state(it)) }
                    .thenByDescending { OfflineScheduler.priority(it.schedule, now) },
            )
        } else {
            available.filterNot { it.id in excluded }
        }
        val card = queue.firstOrNull()
        return NextCardResponse(
            card = card,
            direction = "definition_to_term",
            // The server sends short_definition as the prompt for this direction.
            prompt = card?.shortDefinition?.takeIf { it.isNotEmpty() } ?: card?.definition,
            mode = if (due) "due" else "practice",
            message = if (card == null && due) "No cached cards are due right now." else null,
            practiceComplete = !due && card == null,
            queueCount = queue.size,
            queueBreakdown = ru.lexiloop.app.data.api.QueueBreakdownDto(
                new = queue.count { state(it) == "new" },
                learning = queue.count { state(it) == "learning" || state(it) == "relearning" },
                review = queue.count { state(it) == "review" },
            ),
            showImages = false,
        )
    }

    /** daily_new_limit minus introductions the server knows about (when the
     * overview snapshot is from today) minus introductions made offline today. */
    private suspend fun offlineRemainingNewSlots(now: Long): Int {
        val limit = settingsStore.settings.value.dailyNewLimit
        val meta = offlineCache.overviewMeta()
        val overview = offlineCache.loadOverview()
        val serverIntroduced = if (overview != null && meta != null && isSameLocalDay(meta.savedAt, now)) {
            overview.newIntroducedToday
        } else {
            0
        }
        val offlineIntroduced = offlineCache.pendingReviews().count {
            !it.request.practice && it.previousState == "new" && isSameLocalDay(it.queuedAt, now)
        }
        return (limit - serverIntroduced - offlineIntroduced).coerceAtLeast(0)
    }

    private fun isSameLocalDay(first: Long, second: Long): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(first).atZone(zone).toLocalDate() ==
            java.time.Instant.ofEpochMilli(second).atZone(zone).toLocalDate()
    }

    /** Exact-match answer check used when the LLM judge is unreachable. */
    private fun offlineAnswerMatches(card: FlashcardDto, answer: String): Boolean {
        fun normalize(value: String): String = value.trim().lowercase()
            .replace(Regex("^to\\s+"), "")
            .replace(Regex("\\s+"), " ")
        val target = normalize(answer)
        if (target.isEmpty()) return false
        val candidates = (listOf(card.term) + card.aliases + card.formEntries().map { it.second })
            .map(::normalize)
        return target in candidates
    }

    private suspend fun enqueueOfflineReview(
        card: FlashcardDto,
        judged: JudgeResponse?,
        measured: Long,
        current: StudyUiState,
    ) {
        val timing = devicePrefs.overrides.value.timingFor(current.direction)
        offlineCache.enqueueReview(
            ru.lexiloop.app.data.offline.PendingReview(
                id = java.util.UUID.randomUUID().toString(),
                cardId = card.id,
                request = ReviewRequest(
                    direction = current.direction,
                    answer = current.answer,
                    accepted = judged?.accepted ?: false,
                    judgeScore = null,
                    judgeVerdict = judged?.verdict.orEmpty(),
                    feedback = judged?.feedback.orEmpty(),
                    responseMs = measured,
                    practice = current.practiceMode,
                    hintRevealedLetters = current.hintLetters,
                    hintTotalLetters = Recall.letterCount(card.term),
                    easySeconds = timing?.first,
                    goodSeconds = timing?.second,
                ),
                queuedAt = System.currentTimeMillis(),
                previousState = card.schedule?.state ?: "new",
            ),
        )
        // Mirror the server's rescheduling on the cached copy so the offline
        // queue behaves like real study: a failed card returns in a learning
        // step, a passed one leaves for its next interval. The replay on
        // reconnect makes the server's schedule authoritative again.
        if (!current.practiceMode) {
            val prefs = studyPrefs.value
            val (easySeconds, goodSeconds) = when (current.direction) {
                "definition_to_term" -> prefs.definitionToTermEasySeconds to prefs.definitionToTermGoodSeconds
                "term_to_sentence" -> prefs.termToSentenceEasySeconds to prefs.termToSentenceGoodSeconds
                else -> prefs.termToDefinitionEasySeconds to prefs.termToDefinitionGoodSeconds
            }
            val rating = OfflineScheduler.automaticRating(
                accepted = judged?.accepted ?: false,
                responseMs = measured,
                easySeconds = easySeconds,
                goodSeconds = goodSeconds,
                isDefinitionToTerm = current.direction == "definition_to_term",
                hintRevealedLetters = current.hintLetters,
                hintTotalLetters = Recall.letterCount(card.term),
            )
            val schedule = OfflineScheduler.applyRating(
                card.schedule ?: ru.lexiloop.app.data.api.ScheduleDto(),
                rating,
                settingsStore.settings.value,
                System.currentTimeMillis(),
            )
            offlineCache.updateCardSchedule(card.pool, card.id, schedule)
        }
    }

    private suspend fun offlineCheck(card: FlashcardDto, current: StudyUiState, measured: Long) {
        val accepted = offlineAnswerMatches(card, current.answer)
        val judged = JudgeResponse(
            grading = "binary",
            verdict = if (accepted) "correct" else "incorrect",
            feedback = if (accepted) {
                "Correct — matched offline against the expected word. Progress will sync when you're back online."
            } else {
                "That doesn't match the expected word (checked offline). Progress will sync when you're back online."
            },
            accepted = accepted,
            reviewRecorded = false,
        )
        enqueueOfflineReview(card, judged, measured, current)
        markReviewed(card.id)
        _state.update { it.copy(busy = false, judge = judged, offline = true) }
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
            // Offline: definition -> word answers are checked by exact match.
            if (current.offline || !networkMonitor.online.value) {
                if (current.direction == "definition_to_term") {
                    offlineCheck(card, current, measured)
                } else {
                    _state.update { it.copy(busy = false) }
                    toastBus.error("You're offline — switching to Definition → word study.")
                    load(due = !current.practiceMode)
                }
                return@launch
            }
            val timing = devicePrefs.overrides.value.timingFor(current.direction)
            val request = JudgeRequest(
                answer = current.answer,
                direction = current.direction,
                responseMs = measured,
                practice = current.practiceMode,
                hintRevealedLetters = current.hintLetters,
                hintTotalLetters = Recall.letterCount(card.term),
                easySeconds = timing?.first,
                goodSeconds = timing?.second,
            )
            when (val result = repository.judge(card.id, request)) {
                is ApiResult.Success -> {
                    val judged = result.data
                    if (judged.reviewRecorded) {
                        judged.review?.schedule?.let { fresh ->
                            cacheFreshSchedule(card, fresh)
                        }
                        markReviewed(card.id)
                        _state.update { it.copy(busy = false, judge = judged) }
                        schedulePrefetchNextSession()
                    } else {
                        // Fallback for a lost lock race: record explicitly.
                        val saved = recordReview(card, judged, measured)
                        _state.update { it.copy(busy = false, judge = if (saved) judged else null) }
                        if (saved) schedulePrefetchNextSession()
                    }
                }
                is ApiResult.Error -> {
                    if (result.code == null && current.direction == "definition_to_term") {
                        offlineCheck(card, current, measured)
                    } else if (result.code == null) {
                        _state.update { it.copy(busy = false) }
                        toastBus.error("You're offline — switching to Definition → word study.")
                        load(due = !current.practiceMode)
                    } else {
                        _state.update { it.copy(busy = false) }
                        toastBus.error(result.message)
                    }
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
            val saved = recordReview(card, judged = null, measured)
            _state.update { it.copy(busy = false) }
            if (saved) schedulePrefetchNextSession()
        }
    }

    private suspend fun recordReview(card: FlashcardDto, judged: JudgeResponse?, measured: Long): Boolean {
        if (_state.value.reviewed) return true
        val current = _state.value
        if (current.offline || !networkMonitor.online.value) {
            enqueueOfflineReview(card, judged, measured, current)
            markReviewed(card.id)
            return true
        }
        val timing = devicePrefs.overrides.value.timingFor(current.direction)
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
            easySeconds = timing?.first,
            goodSeconds = timing?.second,
        )
        var result = repository.review(card.id, request)
        if (result is ApiResult.Error && result.code == 409) {
            delay(850)
            result = repository.review(card.id, request)
        }
        return when (result) {
            is ApiResult.Success -> {
                result.data.schedule?.let { fresh -> cacheFreshSchedule(card, fresh) }
                markReviewed(card.id)
                true
            }
            is ApiResult.Error -> {
                if (result.code == null) {
                    // The connection dropped mid-review: keep the result locally.
                    enqueueOfflineReview(card, judged, measured, current)
                    markReviewed(card.id)
                    true
                } else {
                    toastBus.error(result.message)
                    false
                }
            }
        }
    }

    /**
     * Keeps the offline card cache aligned with online progress, so a
     * connection dropping mid-session doesn't resurrect cards that were
     * already studied minutes earlier.
     */
    private suspend fun cacheFreshSchedule(card: FlashcardDto, fresh: ru.lexiloop.app.data.api.ReviewScheduleDto) {
        offlineCache.updateCardSchedule(
            card.pool,
            card.id,
            fresh.toScheduleDto(lastReviewedAt = java.time.Instant.now().toString()),
        )
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
            val ready = nextSession
            if (ready != null && !_state.value.loading) {
                // The prefetched response was fetched after the review was
                // recorded, so it equals what load() would return — minus the
                // round trip.
                clearPrefetchedSession()
                _state.update {
                    it.copy(
                        answer = "", judge = null, revealed = false,
                        reviewed = false, hintLetters = 0, responseMs = null,
                    )
                }
                applySession(
                    ready,
                    due = !current.practiceMode,
                    resetRound = false,
                    adjustedTotal = null,
                    offline = false,
                )
            } else {
                load(due = !current.practiceMode)
            }
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

    // --- Card image editing (the site's topline image button) ---

    fun setImageFromLink(url: String) {
        val card = _state.value.card ?: return
        val link = url.trim()
        if (link.isEmpty() || _state.value.imageBusy) return
        _state.update { it.copy(imageBusy = true) }
        viewModelScope.launch {
            finishImage(repository.setCardImageFromLink(card.id, link), "Image saved")
        }
    }

    fun uploadImage(uri: android.net.Uri) {
        val card = _state.value.card ?: return
        if (_state.value.imageBusy) return
        _state.update { it.copy(imageBusy = true) }
        viewModelScope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                _state.update { it.copy(imageBusy = false) }
                toastBus.error("Could not read that image.")
                return@launch
            }
            finishImage(repository.uploadCardImage(card.id, bytes, "upload.jpg"), "Image saved")
        }
    }

    fun removeImage() {
        val card = _state.value.card ?: return
        if (_state.value.imageBusy) return
        _state.update { it.copy(imageBusy = true) }
        viewModelScope.launch {
            finishImage(repository.removeCardImage(card.id), "Image removed")
        }
    }

    private fun finishImage(result: ApiResult<FlashcardDto>, successMessage: String) {
        when (result) {
            is ApiResult.Success -> {
                _state.update { current ->
                    current.copy(
                        imageBusy = false,
                        session = current.session?.copy(card = result.data),
                    )
                }
                toastBus.success(successMessage)
            }
            is ApiResult.Error -> {
                _state.update { it.copy(imageBusy = false) }
                toastBus.error(result.message)
            }
        }
    }

    fun pronounce(text: String) {
        player.play(text) { toastBus.error("Pronunciation is unavailable right now.") }
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }
}
