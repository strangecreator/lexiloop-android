package ru.lexiloop.app.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.api.JudgeResponse
import ru.lexiloop.app.data.api.NextCardResponse
import ru.lexiloop.app.data.repo.CardImages
import ru.lexiloop.app.ui.common.ErrorBanner

private fun directionTitle(direction: String?): String = when (direction) {
    "definition_to_term" -> "Write the word"
    "term_to_sentence" -> "Write a sentence with this word"
    else -> "Write the definition"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(viewModel: StudyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Study")
                        state.poolName?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                },
                actions = {
                    FilterChip(
                        selected = state.practiceMode,
                        onClick = { viewModel.setPracticeMode(!state.practiceMode) },
                        label = { Text("Practice") },
                    )
                    Spacer(Modifier.size(12.dp))
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.round?.card == null -> EmptyQueue(
                message = state.emptyMessage ?: "Nothing to study right now.",
                error = state.error,
                onReload = viewModel::loadNext,
                padding = innerPadding,
            )

            else -> StudyContent(
                state = state,
                viewModel = viewModel,
                padding = innerPadding,
            )
        }
    }
}

@Composable
private fun EmptyQueue(
    message: String,
    error: String?,
    onReload: () -> Unit,
    padding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        error?.let {
            ErrorBanner(it)
            Spacer(Modifier.height(16.dp))
        }
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onReload) { Text("Reload") }
    }
}

@Composable
private fun StudyContent(
    state: StudyUiState,
    viewModel: StudyViewModel,
    padding: PaddingValues,
) {
    val round = state.round ?: return
    val card = round.card ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        QueueInfo(round)
        state.error?.let { ErrorBanner(it) }

        PromptCard(round, card, state, onPronounce = viewModel::pronounce)

        if (state.phase == StudyPhase.Feedback) {
            state.judge?.let { JudgeCard(it) }
            state.review?.let { review ->
                if (review.automaticRatingLabel.isNotEmpty() && !review.practice) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Rated: ${review.automaticRatingLabel}") },
                    )
                }
            }
            if (state.revealed) {
                AnswerCard(card)
            }
            Button(
                onClick = viewModel::loadNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next")
            }
        } else {
            OutlinedTextField(
                value = state.answer,
                onValueChange = viewModel::onAnswerChange,
                label = { Text(directionTitle(round.direction)) },
                minLines = if (round.direction == "definition_to_term") 1 else 3,
                enabled = state.phase == StudyPhase.Answering,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = viewModel::checkAnswer,
                    enabled = state.phase == StudyPhase.Answering && state.answer.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.phase == StudyPhase.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Check answer")
                    }
                }
                OutlinedButton(
                    onClick = viewModel::showAnswer,
                    enabled = state.phase == StudyPhase.Answering,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Show answer")
                }
            }
        }
    }
}

@Composable
private fun QueueInfo(round: NextCardResponse) {
    val text = if (round.mode == "practice") {
        "Practice · ${round.roundCompleted}/${round.roundTotal}"
    } else {
        val b = round.queueBreakdown
        buildString {
            append("${round.queueCount} in queue")
            if (b != null) append(" · new ${b.new} · learning ${b.learning} · review ${b.review}")
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun PromptCard(
    round: NextCardResponse,
    card: FlashcardDto,
    state: StudyUiState,
    onPronounce: () -> Unit,
) {
    val showTerm = round.direction != "definition_to_term"
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (round.showImages && card.hasImage) {
                AsyncImage(
                    model = CardImages.imageUrl(card),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    round.prompt.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                if (showTerm) {
                    IconButton(onClick = onPronounce) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Pronounce",
                        )
                    }
                }
            }
            if (showTerm) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.ipa.isNotEmpty()) {
                        Text(
                            card.ipa,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (card.partOfSpeech.isNotEmpty()) {
                        Text(
                            card.partOfSpeech,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JudgeCard(judge: JudgeResponse) {
    val positive = judge.accepted
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (positive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (positive) "Accepted · score ${judge.score}/7" else "Not accepted · score ${judge.score}/7",
                style = MaterialTheme.typography.titleMedium,
            )
            if (judge.feedback.isNotEmpty()) {
                Text(judge.feedback, style = MaterialTheme.typography.bodyMedium)
            }
            if (judge.missingOrWrongConcepts.isNotEmpty()) {
                Text(
                    "Missing or wrong: ${judge.missingOrWrongConcepts.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AnswerCard(card: FlashcardDto) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.term, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (card.ipa.isNotEmpty()) {
                    Text(
                        card.ipa,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (card.partOfSpeech.isNotEmpty()) {
                    Text(
                        card.partOfSpeech,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Text(card.definition, style = MaterialTheme.typography.bodyLarge)
            val examples = card.exampleSentences()
            if (examples.isNotEmpty()) {
                Text(
                    "Examples",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                examples.take(3).forEach { example ->
                    Text(
                        "• ${example.sentence}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (card.synonyms.isNotEmpty()) {
                Text(
                    "Synonyms: ${card.synonyms.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
