package ru.lexiloop.app.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.lexiloop.app.data.api.ModelOption
import ru.lexiloop.app.ui.components.FieldLabel
import ru.lexiloop.app.ui.components.LexiButton
import ru.lexiloop.app.ui.components.LexiCheckRow
import ru.lexiloop.app.ui.components.LexiSelect
import ru.lexiloop.app.ui.components.LexiSwitch
import ru.lexiloop.app.ui.components.LexiTextField
import ru.lexiloop.app.ui.components.NumberInput
import ru.lexiloop.app.ui.components.StatusPill
import ru.lexiloop.app.ui.components.ButtonKind
import ru.lexiloop.app.ui.pagePadding
import ru.lexiloop.app.ui.theme.LocalPalette
import ru.lexiloop.app.ui.theme.POOL_ACCENTS
import ru.lexiloop.app.ui.theme.lexiPalette

private val UI_ACCENTS = listOf("emerald", "blue", "teal", "indigo", "violet", "rose", "orange")

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val p = LocalPalette.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form
    val models = state.models

    val generationModel = models.firstOrNull { it.id == form.generationModel }
    val judgeModel = models.firstOrNull { it.id == form.judgeModel }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Flashcard generation
        item(key = "generation") {
            SettingsSection(
                icon = Icons.Filled.AutoAwesome,
                title = "Flashcard generation",
                subtitle = "Choose a public model. LexiLoop handles the router identifier internally.",
            ) {
                SettingsField("Generation model") {
                    ModelPicker(
                        value = form.generationModel,
                        models = models,
                        role = "generation",
                        onSelect = { id -> viewModel.patch { it.copy(generationModel = id) } },
                    )
                }
                SettingsField(generationModel?.tokenLabel ?: "Provider API key", "Encrypted at rest and never returned by the API. Saved once per provider.") {
                    LexiTextField(
                        value = generationModel?.let { state.stagedTokens[it.tokenProvider] } ?: "",
                        onValueChange = { value ->
                            generationModel?.let { viewModel.stageToken(it.tokenProvider, value) }
                        },
                        placeholder = if (generationModel != null && form.tokenStatus[generationModel.tokenProvider] == true) {
                            "••••••••  Leave blank to keep"
                        } else {
                            "Paste ${generationModel?.tokenLabel ?: "API key"}"
                        },
                        monospace = true,
                    )
                }
            }
        }

        // Definition judge
        item(key = "judge") {
            SettingsSection(
                icon = Icons.Filled.Psychology,
                title = "Definition judge",
                subtitle = "Use a fast, inexpensive model independently from generation.",
            ) {
                SettingsField("Judge model") {
                    ModelPicker(
                        value = form.judgeModel,
                        models = models,
                        role = "judge",
                        onSelect = { id -> viewModel.patch { it.copy(judgeModel = id) } },
                    )
                }
                SettingsField(judgeModel?.tokenLabel ?: "Provider API key", "Use the key belonging to the selected provider.") {
                    LexiTextField(
                        value = judgeModel?.let { state.stagedTokens[it.tokenProvider] } ?: "",
                        onValueChange = { value ->
                            judgeModel?.let { viewModel.stageToken(it.tokenProvider, value) }
                        },
                        placeholder = if (judgeModel != null && form.tokenStatus[judgeModel.tokenProvider] == true) {
                            "••••••••  Leave blank to keep"
                        } else {
                            "Paste ${judgeModel?.tokenLabel ?: "API key"}"
                        },
                        monospace = true,
                    )
                }
                ScoreSlider(
                    label = "Accept score",
                    hint = "Answers at or above this score count as understood.",
                    value = form.judgeAcceptanceScore,
                    onValue = { score -> viewModel.patch { it.copy(judgeAcceptanceScore = score) } },
                )
            }
        }

        // Sentence judge
        item(key = "sentence") {
            SettingsSection(
                icon = Icons.Filled.Edit,
                title = "Sentence judge",
                subtitle = "Grades the Word → sentence task: does the sentence use the word correctly and naturally?",
            ) {
                SettingsField("Sentence judge model", "Sentences are graded on a fixed 1–7 usage rubric. Uses the provider key saved above.") {
                    LexiSelect(
                        value = form.sentenceJudgeModel,
                        options = listOf("" to "Same as the definition judge") +
                            models.map { it.id to "${it.label} · ${it.provider.substringBefore(" · ")}" },
                        onSelect = { id -> viewModel.patch { it.copy(sentenceJudgeModel = id) } },
                    )
                }
                ScoreSlider(
                    label = "Accept score",
                    hint = "Sentences at or above this score count as correct usage.",
                    value = form.sentenceAcceptanceScore,
                    onValue = { score -> viewModel.patch { it.copy(sentenceAcceptanceScore = score) } },
                )
            }
        }

        // Card images
        item(key = "images") {
            SettingsSection(
                icon = Icons.Filled.Image,
                title = "Card images",
                subtitle = "An optional picture appears on the flashcard during study.",
            ) {
                SettingsField("Image assistant model", "Reads a pasted page link and points at the right image file when a plain download fails.") {
                    LexiSelect(
                        value = form.imageModel,
                        options = listOf("" to "Same as the generation model") +
                            models.map { it.id to "${it.label} · ${it.provider.substringBefore(" · ")}" },
                        onSelect = { id -> viewModel.patch { it.copy(imageModel = id) } },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Study images", fontSize = 13.sp, fontWeight = FontWeight.W600, color = p.muted)
                        Text(
                            if (form.showCardImages) "Images are shown on flashcards" else "Images stay hidden during study",
                            fontSize = 11.sp,
                            color = p.muted2,
                        )
                    }
                    LexiSwitch(form.showCardImages) { checked -> viewModel.patch { it.copy(showCardImages = checked) } }
                }
                SettingsField("Where images appear", "Applies while study images are on.") {
                    LexiCheckRow(form.showImagesTermToDefinition, { checked ->
                        viewModel.patch { it.copy(showImagesTermToDefinition = checked) }
                    }, "Word → definition tasks")
                    LexiCheckRow(form.showImagesDefinitionToTerm, { checked ->
                        viewModel.patch { it.copy(showImagesDefinitionToTerm = checked) }
                    }, "Definition → word tasks", "a picture can hint at the answer — turn off for stricter recall")
                    LexiCheckRow(form.showImagesTermToSentence, { checked ->
                        viewModel.patch { it.copy(showImagesTermToSentence = checked) }
                    }, "Word → sentence tasks")
                }
                SettingsField("Prefetch upcoming images", "How many of the next flashcards' images load in advance during study. 0 disables prefetching.") {
                    NumberInput(
                        value = form.imagePrefetchCount.toDouble(),
                        onValue = { value -> viewModel.patch { it.copy(imagePrefetchCount = value.toInt()) } },
                        min = 0.0,
                        max = 10.0,
                        round = true,
                    )
                }
            }
        }

        // Saved API keys
        item(key = "keys") {
            val providers = models
                .map { Triple(it.tokenProvider, it.provider.substringBefore(" · "), it.tokenLabel) }
                .distinctBy { it.first }
            if (providers.isNotEmpty()) {
                SettingsSection(
                    icon = Icons.Filled.Key,
                    title = "Saved API keys",
                    subtitle = "One key per provider. Every model of that provider uses it automatically.",
                ) {
                    providers.forEachIndexed { index, (providerId, name, tokenLabel) ->
                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
                        val staged = state.stagedTokens[providerId]
                        val keyState = when {
                            staged == "" -> "Removed on save"
                            staged != null -> "Updated on save"
                            form.tokenStatus[providerId] == true -> "Key saved"
                            else -> "No key"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .background(
                                        when {
                                            staged == "" -> p.red
                                            staged != null || form.tokenStatus[providerId] == true -> p.green
                                            else -> p.border2
                                        },
                                        androidx.compose.foundation.shape.CircleShape,
                                    ),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(name, fontSize = 14.sp, fontWeight = FontWeight.W700, color = p.text)
                                Text(tokenLabel, fontSize = 11.sp, color = p.muted2)
                            }
                            StatusPill(
                                keyState,
                                color = when {
                                    staged == "" -> p.orange
                                    staged != null || form.tokenStatus[providerId] == true -> p.green
                                    else -> p.muted
                                },
                                background = p.surface3,
                            )
                            if (staged != null) {
                                Text(
                                    "Undo",
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .clickable { viewModel.unstageToken(providerId) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.W700,
                                    color = p.muted,
                                )
                            } else if (form.tokenStatus[providerId] == true) {
                                Text(
                                    "Remove",
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .clickable { viewModel.stageToken(providerId, "") }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.W700,
                                    color = p.red,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Study experience
        item(key = "study") {
            SettingsSection(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "Study experience",
                subtitle = "Control prompt direction, appearance, and new-card load.",
            ) {
                SettingsField("Task types", "Due cards rotate through the enabled task types. At least one stays on.") {
                    listOf(
                        "term_to_definition" to "Word → definition",
                        "definition_to_term" to "Definition → word",
                        "term_to_sentence" to "Word → sentence",
                    ).forEach { (id, label) ->
                        LexiCheckRow(
                            checked = form.studyDirections.contains(id),
                            onCheckedChange = { checked ->
                                viewModel.patch { current ->
                                    val next = if (checked) {
                                        listOf("term_to_definition", "definition_to_term", "term_to_sentence")
                                            .filter { it == id || current.studyDirections.contains(it) }
                                    } else {
                                        current.studyDirections.filter { it != id }
                                    }
                                    if (next.isEmpty()) current else current.copy(studyDirections = next)
                                }
                            },
                            label = label,
                        )
                    }
                }
                SettingsField("Appearance") {
                    LexiSelect(
                        value = form.theme,
                        options = listOf("dark" to "Dark", "light" to "Light", "system" to "System"),
                        onSelect = { theme -> viewModel.patch { it.copy(theme = theme) } },
                    )
                }
                SettingsField("Daily new cards") {
                    NumberInput(
                        value = form.dailyNewLimit.toDouble(),
                        onValue = { value -> viewModel.patch { it.copy(dailyNewLimit = value.toInt()) } },
                        min = 0.0,
                        max = 500.0,
                        round = true,
                    )
                }
                // .accent-setting
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(p.surface2, RoundedCornerShape(13.dp))
                        .border(1.dp, p.border, RoundedCornerShape(13.dp))
                        .padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = p.primary2, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Interface color", fontSize = 13.sp, fontWeight = FontWeight.W700, color = p.text)
                            Text("Choose the accent used for actions, charts, and highlights.", fontSize = 11.sp, color = p.muted)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UI_ACCENTS.forEach { accentName ->
                            val swatch = lexiPalette(p.isDark, accentName).primary
                            val active = form.accentColor == accentName
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(p.surface, RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (active) 2.dp else 1.dp,
                                        color = if (active) swatch else p.border2,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable { viewModel.patch { it.copy(accentColor = accentName) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(20.dp).background(swatch, RoundedCornerShape(6.dp)))
                            }
                        }
                    }
                }
            }
        }

        // Text size (device-local)
        item(key = "textsize") {
            val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
            SettingsSection(
                icon = Icons.Filled.FormatSize,
                title = "Text size",
                subtitle = "Stored on this device only and applied immediately.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ru.lexiloop.app.data.repo.DevicePrefs.TEXT_SCALES.forEachIndexed { index, (label, value) ->
                        val active = kotlin.math.abs(fontScale - value) < 0.01f
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(p.surface2, RoundedCornerShape(12.dp))
                                .border(
                                    width = if (active) 2.dp else 1.dp,
                                    color = if (active) p.primary else p.border,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { viewModel.setFontScale(value) }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Aa",
                                fontSize = (14 + index * 3).sp,
                                fontWeight = FontWeight.W800,
                                color = if (active) p.primary2 else p.text,
                            )
                            Text(label, fontSize = 11.sp, color = p.muted, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Automatic review timing
        item(key = "timing") {
            SettingsSection(
                icon = Icons.Filled.Schedule,
                title = "Automatic review timing",
                subtitle = "Correctness is primary; response time chooses Easy, Good, or Hard automatically.",
            ) {
                TimingBand(
                    "Word → definition", "Writing a free-form meaning takes longer.",
                    form.termToDefinitionEasySeconds, form.termToDefinitionGoodSeconds,
                    { value -> viewModel.patch { it.copy(termToDefinitionEasySeconds = value) } },
                    { value -> viewModel.patch { it.copy(termToDefinitionGoodSeconds = value) } },
                )
                TimingBand(
                    "Definition → word", "Recalling and typing one term should be faster.",
                    form.definitionToTermEasySeconds, form.definitionToTermGoodSeconds,
                    { value -> viewModel.patch { it.copy(definitionToTermEasySeconds = value) } },
                    { value -> viewModel.patch { it.copy(definitionToTermGoodSeconds = value) } },
                )
                TimingBand(
                    "Word → sentence", "Composing an original sentence takes the longest.",
                    form.termToSentenceEasySeconds, form.termToSentenceGoodSeconds,
                    { value -> viewModel.patch { it.copy(termToSentenceEasySeconds = value) } },
                    { value -> viewModel.patch { it.copy(termToSentenceGoodSeconds = value) } },
                )
            }
        }

        // Scheduler tuning (advanced)
        item(key = "advanced") {
            SettingsSection(
                icon = Icons.Filled.Speed,
                title = "Scheduler tuning",
                subtitle = "Anki-inspired learning and review parameters",
                collapsible = true,
                open = state.advancedOpen,
                onToggle = viewModel::toggleAdvanced,
            ) {
                SettingsField("Learning steps (minutes)") {
                    LexiTextField(
                        value = form.learningStepsMinutes.joinToString(", ") { formatStep(it) },
                        onValueChange = { value ->
                            viewModel.patch { it.copy(learningStepsMinutes = parseSteps(value)) }
                        },
                    )
                }
                SettingsField("Relearning steps (minutes)") {
                    LexiTextField(
                        value = form.relearningStepsMinutes.joinToString(", ") { formatStep(it) },
                        onValueChange = { value ->
                            viewModel.patch { it.copy(relearningStepsMinutes = parseSteps(value)) }
                        },
                    )
                }
                NumberSetting("Graduating interval (days)", form.graduatingIntervalDays) { value ->
                    viewModel.patch { it.copy(graduatingIntervalDays = value) }
                }
                NumberSetting("Easy interval (days)", form.easyIntervalDays) { value ->
                    viewModel.patch { it.copy(easyIntervalDays = value) }
                }
                NumberSetting("Easy bonus", form.easyBonus) { value ->
                    viewModel.patch { it.copy(easyBonus = value) }
                }
                NumberSetting("Hard multiplier", form.hardMultiplier) { value ->
                    viewModel.patch { it.copy(hardMultiplier = value) }
                }
                NumberSetting("Lapse multiplier", form.lapseMultiplier) { value ->
                    viewModel.patch { it.copy(lapseMultiplier = value) }
                }
                NumberSetting("Minimum ease", form.minimumEase) { value ->
                    viewModel.patch { it.copy(minimumEase = value) }
                }
            }
        }

        // .settings-save
        item(key = "save") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(p.surface, RoundedCornerShape(15.dp))
                    .border(1.dp, p.border2, RoundedCornerShape(15.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Changes apply to future reviews.",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = p.muted,
                )
                LexiButton(
                    if (state.busy) "Saving…" else "Save settings",
                    big = true,
                    leadingIcon = Icons.Filled.Save,
                    enabled = !state.busy,
                    onClick = viewModel::save,
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String? = null,
    statusOk: Boolean = false,
    collapsible: Boolean = false,
    open: Boolean = true,
    onToggle: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface, shape)
            .border(1.dp, lerp(p.border, p.primary, 0.45f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (collapsible) it.clickable { onToggle?.invoke() } else it }
                .padding(horizontal = 17.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(p.primaryBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = p.primary2, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.W700, color = p.text)
                Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = p.muted)
            }
            if (status != null) {
                StatusPill(
                    status,
                    color = if (statusOk) p.green else p.orange,
                    background = (if (statusOk) p.green else p.orange).copy(alpha = 0.09f),
                )
            }
            if (collapsible) {
                Icon(
                    if (open) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = p.muted,
                )
            }
        }
        if (!collapsible || open) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
            Column(
                modifier = Modifier.padding(horizontal = 17.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsField(label: String, hint: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FieldLabel(label)
        content()
        if (hint != null) {
            Text(hint, fontSize = 11.sp, lineHeight = 15.sp, color = p.muted2)
        }
    }
}

@Composable
private fun ScoreSlider(label: String, hint: String, value: Int, onValue: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.W600, color = p.muted, modifier = Modifier.weight(1f))
            Text("$value", fontSize = 13.sp, fontWeight = FontWeight.W800, color = p.text)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(kotlin.math.round(it).toInt().coerceIn(1, 7)) },
            valueRange = 1f..7f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = p.primary,
                activeTrackColor = p.primary,
                inactiveTrackColor = p.surface3,
            ),
        )
        Text(hint, fontSize = 11.sp, color = p.muted2)
    }
}

/** .timing-band: Easy < n sec, Good < m sec, Hard ≥ m sec. */
@Composable
private fun TimingBand(
    title: String,
    description: String,
    easy: Int,
    good: Int,
    setEasy: (Int) -> Unit,
    setGood: (Int) -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface2, RoundedCornerShape(13.dp))
            .border(1.dp, p.border, RoundedCornerShape(13.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.W700, color = p.text)
            Text(description, fontSize = 12.sp, color = p.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            ThresholdBox("EASY", Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("<", fontSize = 13.sp, color = p.text)
                    Box(Modifier.width(64.dp)) {
                        NumberInput(easy.toDouble(), { setEasy(it.toInt()) }, min = 1.0, max = 600.0, round = true)
                    }
                    Text("sec", fontSize = 13.sp, color = p.text)
                }
            }
            ThresholdBox("GOOD", Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("<", fontSize = 13.sp, color = p.text)
                    Box(Modifier.width(64.dp)) {
                        NumberInput(good.toDouble(), { setGood(it.toInt()) }, min = 2.0, max = 900.0, round = true)
                    }
                    Text("sec", fontSize = 13.sp, color = p.text)
                }
            }
            ThresholdBox("HARD", Modifier.weight(1f)) {
                Text("≥ $good sec", fontSize = 13.sp, fontWeight = FontWeight.W600, color = p.text)
            }
        }
    }
}

@Composable
private fun ThresholdBox(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier = modifier
            .background(p.surface, RoundedCornerShape(10.dp))
            .border(1.dp, p.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(label, fontSize = 11.sp, letterSpacing = 0.8.sp, color = p.muted)
        content()
    }
}

@Composable
private fun NumberSetting(label: String, value: Double, onValue: (Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FieldLabel(label)
        NumberInput(value, onValue)
    }
}

/** The web ModelInput: filtered select plus the model description card. */
@Composable
private fun ModelPicker(
    value: String,
    models: List<ModelOption>,
    role: String,
    onSelect: (String) -> Unit,
) {
    val p = LocalPalette.current
    val suitable = models.filter { it.recommendedFor.contains(role) }
    val available = if (suitable.any { it.id == value }) {
        suitable
    } else {
        models.filter { it.id == value || it.recommendedFor.contains(role) }
    }
    val selected = models.firstOrNull { it.id == value }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        LexiSelect(
            value = value,
            options = available.map { it.id to "${it.label} · ${it.provider.substringBefore(" · ")}" },
            onSelect = onSelect,
            placeholder = if (available.isEmpty()) "Loading models…" else "Choose a model",
        )
        if (selected != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.surface2, RoundedCornerShape(12.dp))
                    .border(1.dp, p.border, RoundedCornerShape(12.dp))
                    .padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selected.label, fontSize = 13.sp, fontWeight = FontWeight.W700, color = p.text)
                    if (selected.badge.isNotEmpty()) {
                        Text(
                            selected.badge.uppercase(),
                            modifier = Modifier
                                .background(p.primaryBg, RoundedCornerShape(99.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                            fontSize = 8.sp,
                            letterSpacing = 0.5.sp,
                            color = p.primary2,
                        )
                    }
                }
                Text(selected.provider, fontSize = 10.sp, color = p.primary2)
                if (selected.description.isNotEmpty()) {
                    Text(selected.description, fontSize = 12.sp, lineHeight = 17.sp, color = p.muted)
                }
            }
        } else if (models.isEmpty()) {
            Text("Loading the public model catalog…", fontSize = 11.sp, color = p.muted2)
        }
    }
}

private fun formatStep(value: Double): String =
    if (value == kotlin.math.floor(value)) value.toLong().toString() else value.toString()

private fun parseSteps(value: String): List<Double> =
    value.split(Regex("[ ,]+")).mapNotNull { it.toDoubleOrNull() }.filter { it > 0 }
