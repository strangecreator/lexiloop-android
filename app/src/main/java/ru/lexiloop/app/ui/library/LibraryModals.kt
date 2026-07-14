package ru.lexiloop.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lexiloop.app.data.api.CardWriteBody
import ru.lexiloop.app.data.api.ExampleBody
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.ui.components.ButtonKind
import ru.lexiloop.app.ui.components.FieldLabel
import ru.lexiloop.app.ui.components.FormError
import ru.lexiloop.app.ui.components.LexiButton
import ru.lexiloop.app.ui.components.LexiModal
import ru.lexiloop.app.ui.components.LexiTextArea
import ru.lexiloop.app.ui.components.LexiTextField
import ru.lexiloop.app.ui.components.ModalActions
import ru.lexiloop.app.ui.components.NumberInput
import ru.lexiloop.app.ui.components.ProgressTrack
import ru.lexiloop.app.ui.components.Spinner
import ru.lexiloop.app.ui.theme.LocalPalette
import ru.lexiloop.app.ui.theme.Manrope

private fun csv(value: String): List<String> =
    value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** The web CardEditor modal: every generated field stays editable. */
@Composable
fun CardEditorModal(
    card: FlashcardDto?,
    poolId: Int,
    onClose: () -> Unit,
    onSave: (cardId: Int?, body: CardWriteBody, done: (String?) -> Unit) -> Unit,
) {
    var term by remember { mutableStateOf(card?.term ?: "") }
    var partOfSpeech by remember { mutableStateOf(card?.partOfSpeech ?: "") }
    var ipa by remember { mutableStateOf(card?.ipa ?: "") }
    var shortDefinition by remember { mutableStateOf(card?.shortDefinition ?: "") }
    var definition by remember { mutableStateOf(card?.definition ?: "") }
    var examples by remember {
        mutableStateOf(card?.exampleSentences()?.joinToString("\n") { it.sentence } ?: "")
    }
    var forms by remember {
        mutableStateOf(card?.formEntries()?.joinToString("\n") { "${it.first}: ${it.second}" } ?: "")
    }
    var synonyms by remember { mutableStateOf(card?.synonyms?.joinToString(", ") ?: "") }
    var antonyms by remember { mutableStateOf(card?.antonyms?.joinToString(", ") ?: "") }
    var collocations by remember { mutableStateOf(card?.collocations?.joinToString(", ") ?: "") }
    var aliases by remember { mutableStateOf(card?.aliases?.joinToString(", ") ?: "") }
    var usageNotes by remember { mutableStateOf(card?.usageNotes ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LexiModal(
        title = if (card != null) "Edit “${card.term}”" else "Create a card manually",
        subtitle = "Every generated field remains fully editable.",
        onClose = onClose,
    ) {
        EditorField("Word or collocation") { LexiTextField(term, { term = it }) }
        EditorField("Part of speech") { LexiTextField(partOfSpeech, { partOfSpeech = it }, placeholder = "verb, noun, idiom…") }
        EditorField("IPA") { LexiTextField(ipa, { ipa = it }, placeholder = "without slashes") }
        EditorField("Short definition") { LexiTextField(shortDefinition, { shortDefinition = it }) }
        EditorField("Full definition") { LexiTextArea(definition, { definition = it }) }
        EditorField("Examples", "one sentence per line") { LexiTextArea(examples, { examples = it }) }
        EditorField("Forms", "key: value, one per line") { LexiTextArea(forms, { forms = it }) }
        EditorField("Synonyms", "comma-separated") { LexiTextArea(synonyms, { synonyms = it }, minHeight = 60) }
        EditorField("Collocations") { LexiTextArea(collocations, { collocations = it }, minHeight = 60) }
        EditorField("Antonyms") { LexiTextArea(antonyms, { antonyms = it }, minHeight = 60) }
        EditorField("Accepted aliases") { LexiTextArea(aliases, { aliases = it }, minHeight = 60) }
        EditorField("Usage notes") { LexiTextArea(usageNotes, { usageNotes = it }, minHeight = 60) }
        error?.let { FormError(it) }
        ModalActions {
            LexiButton("Cancel", kind = ButtonKind.Ghost, onClick = onClose)
            LexiButton(
                if (busy) "Saving…" else "Save card",
                enabled = !busy && term.isNotBlank() && definition.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    val body = CardWriteBody(
                        pool = poolId,
                        term = term,
                        partOfSpeech = partOfSpeech,
                        ipa = ipa,
                        shortDefinition = shortDefinition,
                        definition = definition,
                        examples = examples.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                            .map { ExampleBody(sentence = it) },
                        forms = forms.split('\n').mapNotNull { line ->
                            val parts = line.split(':', limit = 2)
                            if (parts.size == 2 && parts[0].isNotBlank()) {
                                parts[0].trim() to parts[1].trim()
                            } else {
                                null
                            }
                        }.toMap(),
                        synonyms = csv(synonyms),
                        antonyms = csv(antonyms),
                        collocations = csv(collocations),
                        aliases = csv(aliases),
                        usageNotes = usageNotes,
                    )
                    onSave(card?.id, body) { failure ->
                        busy = false
                        error = failure
                    }
                },
            )
        }
    }
}

@Composable
private fun EditorField(label: String, sub: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FieldLabel(label, sub)
        content()
    }
}

/** Two-stage bulk generation with live durable-job progress. */
@Composable
fun BulkModal(state: BulkUiState, viewModel: LibraryViewModel) {
    val p = LocalPalette.current
    val job = state.job

    if (job != null) {
        val active = job.isActive
        LexiModal(
            title = "Bulk AI generation",
            subtitle = if (active) {
                "Round ${maxOf(1, job.currentRound)} of ${job.maxRounds} · the job continues if this popup is closed"
            } else {
                "Generation report"
            },
            onClose = { viewModel.closeBulk() },
        ) {
            // .bulk-progress-head
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BulkStat(job.createdCount, "created", Modifier.weight(1f))
                BulkStat(job.skippedCount, "already existed", Modifier.weight(1f))
                BulkStat(job.failedCount, "currently failed", Modifier.weight(1f))
            }
            ProgressTrack(progress = (job.progress / 100.0).toFloat(), height = 10)
            Row(Modifier.fillMaxWidth()) {
                Text(
                    when {
                        job.status == "queued" -> "Waiting for the background worker…"
                        job.status == "running" -> "${job.processedCount} of ${job.totalCount} resolved"
                        else -> "${job.processedCount} of ${job.totalCount} finished"
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W700,
                    color = p.text,
                )
                Text("${"%.1f".format(job.progress)}%", fontSize = 12.sp, color = p.primary2)
            }
            if (active) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(p.primaryBg, RoundedCornerShape(11.dp))
                        .border(1.dp, p.primary.copy(alpha = 0.30f), RoundedCornerShape(11.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = p.primary2, modifier = Modifier.size(17.dp))
                    Text(
                        "Each successful card is saved immediately. Failed items are retried in later rounds with three attempts per request.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = p.muted,
                    )
                }
            }
            if (job.error.isNotEmpty()) FormError(job.error)
            if (!active && job.failedTerms.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(p.red.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .border(1.dp, p.red.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(
                        "${job.failedTerms.size} terms could not be generated",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W700,
                        color = p.red,
                    )
                    Column(
                        Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        job.failedTerms.forEach { failed ->
                            Column {
                                Text(failed.term, fontSize = 12.sp, fontWeight = FontWeight.W700, color = p.text)
                                Text(failed.error, fontSize = 11.sp, lineHeight = 15.sp, color = p.muted)
                                Text(
                                    "${failed.attempts} round attempt${if (failed.attempts == 1) "" else "s"}",
                                    fontSize = 10.sp,
                                    color = p.muted2,
                                )
                            }
                        }
                    }
                }
            }
            ModalActions {
                LexiButton(
                    if (active) "Close and keep running" else "Close",
                    kind = ButtonKind.Ghost,
                    onClick = { viewModel.closeBulk() },
                )
                if (active) {
                    LexiButton("Cancel job", kind = ButtonKind.DangerText, enabled = !state.busy, onClick = { viewModel.cancelBulk() })
                } else {
                    LexiButton("Done", onClick = { viewModel.closeBulk() })
                }
            }
        }
        return
    }

    val preview = state.preview
    if (preview == null) {
        LexiModal(
            title = "Bulk AI generation",
            subtitle = "Stage 1 of 2 · normalize and validate before spending tokens.",
            onClose = { viewModel.closeBulk() },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                FieldLabel("Paste your vocabulary list")
                LexiTextArea(
                    value = state.terms,
                    onValueChange = viewModel::onBulkTermsChange,
                    placeholder = "to abolish (v)\nabuse (n) / to abuse (v)\nadolescent (n / adj)",
                    minHeight = 210,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.surface2, RoundedCornerShape(11.dp))
                    .border(1.dp, p.border, RoundedCornerShape(11.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = p.primary2, modifier = Modifier.size(17.dp))
                Text(
                    "Infinitive to, part-of-speech labels, duplicate grammatical variants, and list separators will be cleaned first.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = p.muted,
                )
            }
            state.error?.let { FormError(it) }
            ModalActions {
                LexiButton("Cancel", kind = ButtonKind.Ghost, onClick = { viewModel.closeBulk() })
                LexiButton(
                    if (state.busy) "Normalizing…" else "Normalize list",
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                    enabled = !state.busy && state.terms.isNotBlank(),
                    onClick = { viewModel.normalize() },
                )
            }
        }
        return
    }

    // Stage 2: review normalized terms and start the durable job.
    LexiModal(
        title = "Review normalized terms",
        subtitle = "Stage 2 of 2 · start a durable background job.",
        onClose = { viewModel.closeBulk() },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BulkStat(preview.normalized.size, "cards ready", Modifier.weight(1f))
            BulkStat(preview.errors.size, "rejected", Modifier.weight(1f))
            BulkStat(
                preview.changes.count { it.status == "duplicate" },
                "duplicates merged",
                Modifier.weight(1f),
            )
        }
        // .normalized-list
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = 180.dp)
                .background(p.surface2, RoundedCornerShape(11.dp))
                .border(1.dp, p.border, RoundedCornerShape(11.dp))
                .padding(12.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ru.lexiloop.app.ui.components.ChipRow(preview.normalized)
            }
        }
        if (preview.errors.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.surface2, RoundedCornerShape(11.dp))
                    .border(1.dp, p.border, RoundedCornerShape(11.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${preview.errors.size} rejected items",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W700,
                    color = p.muted,
                )
                preview.errors.take(20).forEach { rejected ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(rejected.term, fontSize = 11.sp, color = p.red, fontWeight = FontWeight.W700)
                        Text(rejected.error, fontSize = 11.sp, color = p.red, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        // .concurrency-control
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.surface2, RoundedCornerShape(13.dp))
                .border(1.dp, p.border, RoundedCornerShape(13.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text("Concurrent requests", fontSize = 13.sp, fontWeight = FontWeight.W700, color = p.text)
                Text(
                    "The worker retries failures across up to five convergence rounds.",
                    fontSize = 10.sp,
                    color = p.muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(8, 20, 50, 100, 200).forEach { preset ->
                    val active = state.batchSize == preset
                    Text(
                        preset.toString(),
                        modifier = Modifier
                            .weight(1f)
                            .background(if (active) p.primaryBg else p.surface, RoundedCornerShape(8.dp))
                            .border(1.dp, if (active) p.primary else p.border, RoundedCornerShape(8.dp))
                            .clickable { viewModel.onBulkBatchSize(preset) }
                            .padding(vertical = 7.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W700,
                        color = if (active) p.text else p.muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        state.error?.let { FormError(it) }
        ModalActions {
            LexiButton("Back", kind = ButtonKind.Ghost, onClick = { viewModel.bulkBack() })
            LexiButton(
                if (state.busy) "Starting…" else "Start ${preview.normalized.size}-card job",
                leadingIcon = Icons.Filled.AutoAwesome,
                enabled = !state.busy && preview.normalized.isNotEmpty(),
                onClick = { viewModel.startBulk() },
            )
        }
    }
}

@Composable
private fun BulkStat(value: Int, label: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier = modifier
            .background(p.surface2, RoundedCornerShape(11.dp))
            .border(1.dp, p.border, RoundedCornerShape(11.dp))
            .padding(13.dp),
    ) {
        Text(
            value.toString(),
            fontFamily = Manrope,
            fontSize = 22.sp,
            fontWeight = FontWeight.W800,
            color = p.text,
        )
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            color = p.muted,
        )
    }
}
