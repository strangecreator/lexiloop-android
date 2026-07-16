package ru.lexiloop.app.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ru.lexiloop.app.ui.components.CardImageControls
import ru.lexiloop.app.ui.components.LexiModal
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.api.JudgeResponse
import ru.lexiloop.app.data.repo.CardImages
import ru.lexiloop.app.ui.components.ButtonKind
import ru.lexiloop.app.ui.components.EmptyState
import ru.lexiloop.app.ui.components.LexiButton
import ru.lexiloop.app.ui.components.LexiTextArea
import ru.lexiloop.app.ui.components.LoaderView
import ru.lexiloop.app.ui.components.ProgressTrack
import ru.lexiloop.app.ui.components.StatusPill
import ru.lexiloop.app.ui.pagePadding
import ru.lexiloop.app.ui.theme.LocalPalette
import ru.lexiloop.app.ui.theme.Manrope

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StudyScreen(viewModel: StudyViewModel = hiltViewModel()) {
    val p = LocalPalette.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val padding = pagePadding()

    // Swipe-to-advance state: hoisted above the early returns so remember
    // order stays stable across loading/empty/card phases.
    val swipeScope = rememberCoroutineScope()
    val swipeOffset = remember { Animatable(0f) }
    var cardWidth by remember { mutableStateOf(0) }

    // Like the site, (re)entering the page starts a fresh due round.
    LaunchedEffect(Unit) { viewModel.onPageShown() }

    if (state.loading && state.session == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoaderView("Choosing the right card…")
        }
        return
    }

    val card = state.card
    if (card == null || state.loading) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoaderView("Choosing the right card…")
            }
            return
        }
        // .center-stage empty state with practice actions
        val practiceComplete = state.practiceMode && state.session?.practiceComplete == true
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .wrapContentSize(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            EmptyState(
                icon = if (practiceComplete) Icons.Filled.Refresh else Icons.Filled.Check,
                title = if (practiceComplete) "Practice round complete" else "You are caught up",
                text = state.session?.message ?: "No cards are due right now.",
            )
            if (state.practiceMode) {
                LexiButton("Practice again", leadingIcon = Icons.Filled.Refresh, onClick = viewModel::startPractice, modifier = Modifier.fillMaxWidth())
                LexiButton("Return to due reviews", kind = ButtonKind.Secondary, onClick = viewModel::returnToDue, modifier = Modifier.fillMaxWidth())
            } else {
                LexiButton("Practice all cards now", leadingIcon = Icons.Filled.Refresh, onClick = viewModel::startPractice, modifier = Modifier.fillMaxWidth())
                LexiButton("Check due cards", kind = ButtonKind.Secondary, onClick = viewModel::reload, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }

    val session = state.session ?: return
    val judge = state.judge
    val answered = judge != null || state.revealed
    // The card can be swiped away only once the task is fully completed.
    val canSwipe = answered && state.reviewed && !state.busy

    // A freshly loaded card slides in from the right after a swipe-out.
    LaunchedEffect(card.id) {
        if (swipeOffset.value != 0f) {
            swipeOffset.snapTo(cardWidth * 0.85f)
            swipeOffset.animateTo(
                0f,
                spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    // .study-card border color mirrors correct/wrong state
    val cardBorder by animateColorAsState(
        targetValue = when {
            judge?.accepted == true -> p.green.copy(alpha = 0.72f)
            judge != null -> p.red.copy(alpha = 0.68f)
            else -> p.border
        },
        label = "cardBorder",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            // imePadding must sit OUTSIDE verticalScroll: it shrinks the
            // scroll viewport to the area above the keyboard, which is what
            // lets bringIntoView place the answer box right above the IME.
            // Inside the scrollable it only grows the content, and the field
            // counts as "visible" while actually sitting behind the keyboard.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(padding),
    ) {
        // .study-progress
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (state.practiceMode) "PRACTICE ROUND" else "DUE REVIEW ROUND"),
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.W700,
                    color = p.muted,
                )
                Text(
                    "${state.roundCompleted} done · ${state.remaining} left" +
                        if (state.practiceMode) " in this pool" else "",
                    fontSize = 10.sp,
                    color = p.muted2,
                )
            }
            // .practice-switch
            Text(
                if (state.practiceMode) "Due reviews" else "Practice all",
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(p.surface, RoundedCornerShape(9.dp))
                    .border(1.dp, p.border, RoundedCornerShape(9.dp))
                    .clickable { if (state.practiceMode) viewModel.returnToDue() else viewModel.startPractice() }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.W700,
                color = p.muted,
            )
        }
        if (state.offline) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.orange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .border(1.dp, p.orange.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = p.orange,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Offline — Definition → word only. Progress is saved on this device and syncs automatically.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = p.text,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        ProgressTrack(progress = state.progress)
        val breakdown = session.queueBreakdown
        if (!state.practiceMode && breakdown != null && state.remaining > 0) {
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (breakdown.new > 0) QueueChip("${breakdown.new} new")
                if (breakdown.learning > 0) QueueChip("${breakdown.learning} learning")
                if (breakdown.review > 0) QueueChip("${breakdown.review} review")
            }
        }
        Spacer(Modifier.height(18.dp))

        // .study-card — swipe left to advance once the task is complete.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { cardWidth = it.width }
                .graphicsLayer {
                    val width = cardWidth.toFloat().coerceAtLeast(1f)
                    translationX = swipeOffset.value
                    rotationZ = (swipeOffset.value / width) * 3.5f
                    alpha = 1f - (kotlin.math.abs(swipeOffset.value) / width) * 0.35f
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = canSwipe,
                    state = rememberDraggableState { delta ->
                        // Follow the finger; rightward drags rubber-band.
                        val resisted = if (swipeOffset.value > 0f) delta / 3f else delta
                        val next = (swipeOffset.value + resisted)
                            .coerceIn(-cardWidth.toFloat(), cardWidth * 0.18f)
                        swipeScope.launch { swipeOffset.snapTo(next) }
                    },
                    onDragStopped = { velocity ->
                        val width = cardWidth.toFloat().coerceAtLeast(1f)
                        val advance = swipeOffset.value < -width * 0.3f ||
                            (velocity < -1600f && swipeOffset.value < -width * 0.08f)
                        if (advance) {
                            // Grabbing the card mid-flight cancels this animation
                            // (and the advance) — the finger keeps control.
                            swipeOffset.animateTo(
                                -width * 1.12f,
                                tween(durationMillis = 200, easing = FastOutLinearInEasing),
                            )
                            viewModel.next()
                        } else {
                            swipeOffset.animateTo(
                                0f,
                                spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
                            )
                        }
                    },
                )
                .clip(RoundedCornerShape(18.dp))
                .background(p.surface, RoundedCornerShape(18.dp))
                .border(1.dp, cardBorder, RoundedCornerShape(18.dp)),
        ) {
            // .card-topline — with the quiet diagonal "tape" keyed by task
            // type, so a sentence task never reads as a definition task.
            var imageEditor by remember(card.id) { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .taskTape(state.direction, p.border.copy(alpha = 0.42f))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    when (state.direction) {
                        "definition_to_term" -> "RECALL THE WORD"
                        "term_to_sentence" -> "USE THIS WORD IN A SENTENCE"
                        else -> "EXPLAIN THIS WORD"
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    letterSpacing = 0.9.sp,
                    fontWeight = FontWeight.W600,
                    color = p.muted,
                )
                // .topline-image-button
                if (!state.offline) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { imageEditor = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (card.hasImage) Icons.Filled.Image else Icons.Filled.AddPhotoAlternate,
                            contentDescription = if (card.hasImage) "Change this card's image" else "Add an image to this card",
                            tint = p.muted,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                StatusPill(
                    if (state.practiceMode) "practice" else card.schedule?.state ?: "new",
                    color = p.muted,
                    background = p.surface3,
                )
            }
            if (imageEditor) {
                LexiModal(
                    title = "Card image",
                    subtitle = "Shown on the flashcard for “${card.term}”.",
                    onClose = { imageEditor = false },
                ) {
                    CardImageControls(
                        card = card,
                        busy = state.imageBusy,
                        onLink = viewModel::setImageFromLink,
                        onUpload = viewModel::uploadImage,
                        onRemove = viewModel::removeImage,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))

            // .study-prompt — the image emerges full-bleed behind the prompt
            // with the configured reveal animation, like the site's mobile UI.
            val prefs by viewModel.studyPrefs.collectAsStateWithLifecycle()
            val wantsImage = prefs.imagesEnabledFor(state.direction) && card.hasImage
            var visualShowing by remember(card.id, card.imageKey) { mutableStateOf(false) }
            val overImage = wantsImage && visualShowing
            Box(Modifier.fillMaxWidth().clipToBounds()) {
                if (wantsImage) {
                    val animation = RevealAnimations.forCard(card.id, prefs.imageAnimations)
                    PromptVisual(
                        thumbUrl = CardImages.imageUrl(card.id, card.imageKey, thumb = true),
                        fullUrl = CardImages.imageUrl(card.id, card.imageKey),
                        animation = animation,
                        durationSeconds = RevealAnimations.durationSeconds(
                            animation,
                            prefs.imageAnimationDurations,
                        ),
                        onVisible = { visualShowing = it },
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // .study-prompt.has-image .prompt-content: white + shadow
                    val promptShadow = if (overImage) {
                        androidx.compose.material3.LocalTextStyle.current.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                blurRadius = 22f,
                            ),
                        )
                    } else {
                        androidx.compose.material3.LocalTextStyle.current
                    }
                    Text(
                        session.prompt.orEmpty(),
                        fontFamily = Manrope,
                        fontSize = if (state.showsTerm) 32.sp else 22.sp,
                        lineHeight = if (state.showsTerm) 38.sp else 30.sp,
                        fontWeight = FontWeight.W700,
                        color = if (overImage) androidx.compose.ui.graphics.Color.White else p.text,
                        textAlign = TextAlign.Center,
                        style = promptShadow,
                    )
                    if (state.showsTerm && card.ipa.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "/${card.ipa}/",
                                fontSize = 16.sp,
                                color = if (overImage) {
                                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f)
                                } else {
                                    p.muted
                                },
                                style = promptShadow,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Pronounce",
                                tint = if (overImage) androidx.compose.ui.graphics.Color.White else p.primary2,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { viewModel.pronounce(card.term) },
                            )
                        }
                    }
                    if (state.showsTerm && card.partOfSpeech.isNotEmpty()) {
                        Spacer(Modifier.height(13.dp))
                        PosTag(card.partOfSpeech, overImage = overImage)
                    }
                    if (!state.showsTerm) {
                        Spacer(Modifier.height(24.dp))
                        RecallHints(
                            card = card,
                            revealed = state.hintLetters,
                            answered = answered,
                            onReveal = viewModel::revealHintLetter,
                        )
                    }
                }
            }

            if (!answered) {
                // .answer-area
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    Text(
                        when (state.direction) {
                            "definition_to_term" -> "Type the English word or accepted phrase"
                            "term_to_sentence" -> "Write one sentence using this word"
                            else -> "Write the meaning in your own words"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600,
                        color = p.muted,
                    )
                    Spacer(Modifier.height(7.dp))
                    // When the soft keyboard opens it can cover the answer
                    // box; scroll it into the shrunken viewport once the IME
                    // animation has settled.
                    val answerBring = remember {
                        androidx.compose.foundation.relocation.BringIntoViewRequester()
                    }
                    val answerScope = rememberCoroutineScope()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(answerBring)
                            .onFocusEvent { focus ->
                                if (focus.hasFocus && prefs.scrollToAnswerBox) {
                                    answerScope.launch {
                                        kotlinx.coroutines.delay(360)
                                        answerBring.bringIntoView()
                                    }
                                }
                            },
                    ) {
                        LexiTextArea(
                            value = state.answer,
                            onValueChange = viewModel::onAnswerChange,
                            placeholder = when (state.direction) {
                                "definition_to_term" -> "Your answer…"
                                "term_to_sentence" -> "Any natural sentence that shows the meaning…"
                                else -> "A clear paraphrase is enough…"
                            },
                            minHeight = 115,
                            onSubmit = viewModel::checkAnswer,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LexiButton(
                            "Show answer",
                            kind = ButtonKind.Ghost,
                            onClick = viewModel::showAnswer,
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                        )
                        LexiButton(
                            if (state.busy) "Checking…" else "Check answer",
                            onClick = viewModel::checkAnswer,
                            enabled = !state.busy && state.answer.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                // .result-area
                judge?.let { JudgeBanner(it) }
                AnswerReveal(card = card, judge = judge, onPronounce = { viewModel.pronounce(card.term) })
                // .next-block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 19.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = p.primary2, modifier = Modifier.size(16.dp))
                        Text(
                            when {
                                judge != null -> humanDuration(state.responseMs ?: 0)
                                state.reviewed -> "Saved"
                                else -> "Answer revealed"
                            } + if (state.practiceMode) " · Practice" else "",
                            fontSize = 13.sp,
                            color = p.muted,
                        )
                    }
                    LexiButton(
                        if (state.busy) "Loading…" else "Next task",
                        trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = viewModel::next,
                        enabled = !state.busy,
                    )
                }
            }
        }

        Spacer(Modifier.height(13.dp))
        // Block-card action (replaces the desktop ⌘− shortcut)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .clickable(enabled = !state.busy) { viewModel.suspendCurrent() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Block, contentDescription = null, tint = p.muted2, modifier = Modifier.size(14.dp))
            Text("Block this card from future study", fontSize = 12.sp, color = p.muted2)
        }
    }
}

/**
 * The site's `.card-topline.task-*` texture: wide soft diagonal bands in the
 * border tone. Word → sentence slopes one way, definition → word the other,
 * and word → definition stays plain — texture instead of color, so it is
 * visible at a glance without shouting.
 */
private fun Modifier.taskTape(direction: String, color: androidx.compose.ui.graphics.Color): Modifier =
    when (direction) {
        "term_to_sentence" -> tapeBands(color, mirrored = false)
        "definition_to_term" -> tapeBands(color, mirrored = true)
        else -> this
    }

private fun Modifier.tapeBands(color: androidx.compose.ui.graphics.Color, mirrored: Boolean): Modifier =
    // The angled strokes overshoot the strip's edges; keep them inside like a
    // CSS background.
    clipToBounds().drawBehind {
        val band = 9.dp.toPx()
        // 18dp perpendicular pitch, converted to x-spacing for 45° lines.
        val pitch = 18.dp.toPx() * 1.4142135f
        var x = -size.height
        while (x < size.width + size.height) {
            if (mirrored) {
                drawLine(
                    color,
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x + size.height, size.height),
                    strokeWidth = band,
                )
            } else {
                drawLine(
                    color,
                    start = androidx.compose.ui.geometry.Offset(x, size.height),
                    end = androidx.compose.ui.geometry.Offset(x + size.height, 0f),
                    strokeWidth = band,
                )
            }
            x += pitch
        }
    }

/** `.queue-chips span` — muted on surface3, like the current site styling. */
@Composable
private fun QueueChip(text: String) {
    val p = LocalPalette.current
    Text(
        text,
        modifier = Modifier
            .background(p.surface3, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.W700,
        color = p.muted,
    )
}

@Composable
private fun PosTag(text: String, overImage: Boolean = false) {
    val p = LocalPalette.current
    val white = androidx.compose.ui.graphics.Color.White
    Text(
        text.uppercase(),
        modifier = Modifier
            .border(
                1.dp,
                if (overImage) white.copy(alpha = 0.4f) else p.border2,
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        fontSize = 10.sp,
        letterSpacing = 0.6.sp,
        color = if (overImage) white.copy(alpha = 0.85f) else p.muted,
    )
}

/** .recall-hints: masked examples, collocations, and the letter-hint button. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RecallHints(card: FlashcardDto, revealed: Int, answered: Boolean, onReveal: () -> Unit) {
    val p = LocalPalette.current
    val examples = card.exampleSentences().take(3)
        .map { Recall.maskAnswer(it.sentence, card) }
        .filter { it.contains("_____") }
    val collocations = card.collocations.take(3)
        .map { Recall.maskAnswer(it, card) }
        .filter { it.contains("_____") }
    val total = Recall.letterCount(card.term)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface2.copy(alpha = 0.82f), RoundedCornerShape(14.dp))
            .border(1.dp, p.border, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.HelpOutline, contentDescription = null, tint = p.primary2, modifier = Modifier.size(16.dp))
            Text("Context clues", fontSize = 13.sp, fontWeight = FontWeight.W700, color = p.text)
            Text(
                card.partOfSpeech.ifEmpty { "English term" },
                modifier = Modifier
                    .background(p.surface3, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color = p.muted,
            )
        }
        // .letter-hint — wraps at word boundaries; fully revealed once answered.
        val shownLetters = if (answered) total else revealed
        val letterSize = if (Recall.base(card.term).length > 24) 12.sp else 14.sp
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, p.border2, RoundedCornerShape(9.dp))
                .clickable(enabled = !answered && revealed < total, onClick = onReveal)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Recall.maskedWords(card.term, shownLetters).forEach { word ->
                Text(
                    word,
                    fontFamily = FontFamily.Monospace,
                    fontSize = letterSize,
                    color = p.text,
                    softWrap = false,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier.align(Alignment.CenterVertically),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (answered) "$total/$total" else "$revealed/$total",
                    fontSize = 10.sp,
                    color = p.muted2,
                )
            }
        }
        if (examples.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                examples.forEachIndexed { index, sentence ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(p.primary.copy(alpha = 0.12f), RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.W700, color = p.primary2)
                        }
                        Text(sentence, fontSize = 15.sp, lineHeight = 21.sp, color = p.text, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (collocations.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text("Common use", fontSize = 12.sp, color = p.muted)
                collocations.forEach { collocation ->
                    Text(
                        collocation,
                        modifier = Modifier
                            .background(p.surface, RoundedCornerShape(8.dp))
                            .border(1.dp, p.border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        fontSize = 13.sp,
                        color = p.text,
                    )
                }
            }
        }
        if (examples.isEmpty() && collocations.isEmpty()) {
            Text(
                "Think of the word that best matches this definition and its grammatical role.",
                fontSize = 14.sp,
                color = p.muted,
            )
        }
        if (revealed > 0) {
            val share = revealed.toFloat() / maxOf(1, total)
            Text(
                when {
                    share > 0.4f -> "More than 40% revealed: this review counts as Again."
                    share > 0.2f -> "More than 20% revealed: the rating is capped at Hard."
                    else -> "A letter hint was used: the rating is capped at Good."
                },
                fontSize = 11.sp,
                color = p.orange,
            )
        }
    }
}

/** .judge-banner with the score orb. */
@Composable
private fun JudgeBanner(judge: JudgeResponse) {
    val p = LocalPalette.current
    val accepted = judge.accepted
    val binary = judge.grading == "binary"
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (accepted) p.greenBg.copy(alpha = 0.35f) else p.redBg.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // .score-orb
            Box(
                Modifier
                    .size(48.dp)
                    .background(p.surface3, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    binary && accepted -> Icon(Icons.Filled.Check, contentDescription = null, tint = p.green, modifier = Modifier.size(24.dp))
                    binary -> Icon(Icons.Filled.Close, contentDescription = null, tint = p.red, modifier = Modifier.size(24.dp))
                    else -> Text(
                        "${judge.score}",
                        fontFamily = Manrope,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W800,
                        color = p.primary2,
                    )
                }
            }
            Column {
                Text(
                    when {
                        accepted -> "Correct"
                        binary -> "Incorrect"
                        else -> humanVerdict(judge.verdict)
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                    color = p.text,
                )
                val feedback = distinctFeedback(judge)
                if (feedback.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(feedback, fontSize = 14.sp, lineHeight = 19.sp, color = p.muted)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (accepted) p.green.copy(alpha = 0.38f) else p.red.copy(alpha = 0.35f)),
        )
    }
}

/** .answer-reveal: term, pronounce, definition, example blockquotes, chips. */
@Composable
private fun AnswerReveal(card: FlashcardDto, judge: JudgeResponse?, onPronounce: () -> Unit) {
    val p = LocalPalette.current
    val tint = when {
        judge?.accepted == true -> p.greenBg.copy(alpha = 0.20f)
        judge != null -> p.redBg.copy(alpha = 0.18f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface2)
            .background(tint)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    card.term,
                    fontFamily = Manrope,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    color = p.text,
                )
                if (card.ipa.isNotEmpty()) {
                    Text("/${card.ipa}/", fontSize = 14.sp, color = p.muted)
                }
            }
            // .answer-audio
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, p.border, RoundedCornerShape(10.dp))
                    .clickable(onClick = onPronounce),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Pronounce", tint = p.primary2, modifier = Modifier.size(17.dp))
            }
        }
        Text(
            card.definition,
            fontSize = 16.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.W500,
            color = p.text,
        )
        val examples = card.exampleSentences().take(3)
        if (examples.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                examples.forEach { example ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(p.surface.copy(alpha = 0.54f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text("“${example.sentence}”", fontSize = 14.sp, lineHeight = 20.sp, color = p.text)
                        if (example.note.isNotEmpty()) {
                            Text(example.note, fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        val chips = card.synonyms.take(4) + card.collocations.take(3)
        if (chips.isNotEmpty()) {
            ru.lexiloop.app.ui.components.ChipRow(chips)
        }
    }
}

private fun humanVerdict(verdict: String): String =
    verdict.split('_').joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercase() }
    }

private fun distinctFeedback(judge: JudgeResponse): String {
    val title = (if (judge.accepted) "correct" else if (judge.grading == "binary") "incorrect" else humanVerdict(judge.verdict))
        .lowercase().trimEnd('.', '!', '?').trim()
    val feedback = judge.feedback.lowercase().trimEnd('.', '!', '?').trim()
    val generic = setOf("correct", "right", "exact", "incorrect", "wrong")
    return if (feedback.isEmpty() || feedback == title || feedback in generic) "" else judge.feedback
}

private fun humanDuration(milliseconds: Long): String {
    val seconds = maxOf(0L, Math.round(milliseconds / 1000.0))
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (rest > 0) "${minutes}m ${rest}s" else "${minutes}m"
}
