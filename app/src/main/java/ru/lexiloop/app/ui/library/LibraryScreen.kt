package ru.lexiloop.app.ui.library

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.ui.components.Badge
import ru.lexiloop.app.ui.components.CardImageControls
import ru.lexiloop.app.ui.components.ButtonKind
import ru.lexiloop.app.ui.components.ChipRow
import ru.lexiloop.app.ui.components.EmptyState
import ru.lexiloop.app.ui.components.LexiButton
import ru.lexiloop.app.ui.components.LexiModal
import ru.lexiloop.app.ui.components.LexiTextField
import ru.lexiloop.app.ui.components.LoaderView
import ru.lexiloop.app.ui.components.ModalActions
import ru.lexiloop.app.ui.components.StatusPill
import ru.lexiloop.app.ui.pagePadding
import ru.lexiloop.app.ui.theme.LocalPalette
import ru.lexiloop.app.ui.theme.Manrope

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val p = LocalPalette.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bulk by viewModel.bulk.collectAsStateWithLifecycle()
    val activePoolId by viewModel.activePoolId.collectAsStateWithLifecycle()
    val pools by viewModel.pools.collectAsStateWithLifecycle()

    var editorCard by remember { mutableStateOf<FlashcardDto?>(null) }
    var manualOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FlashcardDto?>(null) }

    if (activePoolId == null) {
        Box(Modifier.fillMaxSize().padding(pagePadding()), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "Choose or create a pool",
                text = "Pools keep different vocabulary goals separate.",
            )
        }
        return
    }
    val selected = pools.firstOrNull { it.id == activePoolId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // .generator-panel
        item(key = "generator") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(23.dp))
                    .background(p.surface, RoundedCornerShape(23.dp))
                    .border(1.dp, p.primary.copy(alpha = 0.28f), RoundedCornerShape(23.dp))
                    .padding(23.dp),
            ) {
                Badge("One-field creation", icon = Icons.Filled.AutoAwesome)
                Spacer(Modifier.height(15.dp))
                Text(
                    "Add a word. AI builds the card.",
                    fontFamily = Manrope,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.W700,
                    color = p.text,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Definition, IPA, forms, examples, synonyms, collocations, and usage notes are generated automatically.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = p.muted,
                )
                Spacer(Modifier.height(22.dp))
                LexiTextField(
                    value = state.term,
                    onValueChange = viewModel::onTermChange,
                    placeholder = "Type a word or collocation…",
                    minHeight = 50,
                )
                Spacer(Modifier.height(9.dp))
                LexiButton(
                    if (state.generating) "Generating…" else "Generate",
                    leadingIcon = Icons.Filled.AutoAwesome,
                    onClick = viewModel::generate,
                    enabled = !state.generating && state.term.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    big = true,
                )
            }
        }

        // .library-toolbar
        item(key = "toolbar") {
            Column(Modifier.padding(top = 10.dp)) {
                Text(selected?.name ?: "Library", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Text(
                    if (state.loading) {
                        "Loading cards…"
                    } else if (state.total > 0) {
                        "${state.firstIndex}–${state.lastIndex} of ${state.total} cards"
                    } else {
                        "0 visible cards"
                    },
                    fontSize = 13.sp,
                    color = p.muted,
                )
                Spacer(Modifier.height(12.dp))
                // .search
                LexiTextField(
                    value = state.search,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = "Search cards",
                    minHeight = 41,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LexiButton(
                        "Bulk AI",
                        kind = ButtonKind.Secondary,
                        leadingIcon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        onClick = viewModel::openBulk,
                        modifier = Modifier.weight(1f),
                    )
                    LexiButton(
                        "Manual card",
                        kind = ButtonKind.Secondary,
                        leadingIcon = Icons.Filled.Add,
                        onClick = { manualOpen = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (state.loading) {
            item(key = "loader") { LoaderView("Loading cards…") }
        } else {
            items(state.cards, key = { it.id }) { card ->
                LibraryCard(
                    card = card,
                    expanded = state.expandedCardId == card.id,
                    busy = state.busyCardId == card.id,
                    onToggle = { viewModel.toggleExpanded(card.id) },
                    onEdit = { editorCard = card },
                    onDelete = { deleting = card },
                    onPronounce = { viewModel.pronounce(card.term) },
                    onSuspendToggle = {
                        if (card.suspended) viewModel.unsuspendCard(card) else viewModel.suspendCardAction(card)
                    },
                    imageBusy = state.imageBusyCardId == card.id,
                    onImageLink = { url -> viewModel.setImageFromLink(card, url) },
                    onImageUpload = { uri -> viewModel.uploadImage(card, uri) },
                    onImageRemove = { viewModel.removeImage(card) },
                )
            }
            if (state.cards.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "No cards found",
                        text = "Add a term above and press Generate. Editing stays out of the way until you need it.",
                    )
                }
            }
            // .pagination (mobile: Page X of Y with arrows)
            if (state.totalPages > 1) {
                item(key = "pagination") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(p.surface, RoundedCornerShape(15.dp))
                            .border(1.dp, p.border, RoundedCornerShape(15.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PageArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, enabled = state.page > 1) {
                            viewModel.setPage(state.page - 1)
                        }
                        Text(
                            "Page ${state.page} of ${state.totalPages}",
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            color = p.muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        PageArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, enabled = state.page < state.totalPages) {
                            viewModel.setPage(state.page + 1)
                        }
                    }
                }
            }
        }
    }

    // --- Modals ---

    if (manualOpen || editorCard != null) {
        CardEditorModal(
            card = editorCard,
            poolId = activePoolId ?: 0,
            onClose = { manualOpen = false; editorCard = null },
            onSave = { cardId, body, done ->
                viewModel.saveCard(cardId, body) { failure ->
                    if (failure == null) {
                        manualOpen = false
                        editorCard = null
                    }
                    done(failure)
                }
            },
        )
    }

    deleting?.let { card ->
        LexiModal(
            title = "Delete “${card.term}”?",
            subtitle = "The card and its review history are removed permanently.",
            onClose = { deleting = null },
        ) {
            ModalActions {
                LexiButton("Cancel", kind = ButtonKind.Ghost, onClick = { deleting = null })
                LexiButton("Delete card", kind = ButtonKind.Danger, onClick = {
                    viewModel.delete(card)
                    deleting = null
                })
            }
        }
    }

    if (bulk.open) {
        BulkModal(state = bulk, viewModel = viewModel)
    }
}

@Composable
private fun PageArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(p.surface2, RoundedCornerShape(10.dp))
            .border(1.dp, p.border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) p.muted else p.muted.copy(alpha = 0.38f),
            modifier = Modifier.size(17.dp),
        )
    }
}

/** .library-card with the expandable .card-details block. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LibraryCard(
    card: FlashcardDto,
    expanded: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPronounce: () -> Unit,
    onSuspendToggle: () -> Unit,
    imageBusy: Boolean,
    onImageLink: (String) -> Unit,
    onImageUpload: (android.net.Uri) -> Unit,
    onImageRemove: () -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(p.surface, RoundedCornerShape(15.dp))
            .border(1.dp, if (expanded) p.border2 else p.border, RoundedCornerShape(15.dp)),
    ) {
        // .card-summary
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        card.term,
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W700,
                        fontFamily = Manrope,
                        color = p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (card.partOfSpeech.isNotEmpty()) {
                        Text(
                            card.partOfSpeech.uppercase(),
                            modifier = Modifier
                                .border(1.dp, p.border2, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            letterSpacing = 0.6.sp,
                            color = p.muted,
                        )
                    }
                }
                val scheduleState = if (card.suspended) "blocked" else card.schedule?.state ?: "new"
                StatusPill(
                    scheduleState,
                    color = when (scheduleState) {
                        "blocked" -> p.red
                        "review" -> p.green
                        "learning", "relearning" -> p.orange
                        else -> p.muted
                    },
                    background = p.surface3,
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = p.muted,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                card.shortDefinition.ifEmpty { card.definition },
                fontSize = 14.sp,
                color = p.muted,
                maxLines = if (expanded) 10 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.surface2)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DetailBlock("DEFINITION") {
                    Text(card.definition, fontSize = 15.sp, lineHeight = 22.sp, color = p.text)
                }
                val examples = card.exampleSentences()
                if (examples.isNotEmpty()) {
                    DetailBlock("EXAMPLES") {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            examples.forEach { example ->
                                Row {
                                    Box(
                                        Modifier
                                            .width(2.dp)
                                            .height(20.dp)
                                            .background(p.primary),
                                    )
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text("“${example.sentence}”", fontSize = 14.sp, color = p.muted, lineHeight = 19.sp)
                                        if (example.note.isNotEmpty()) {
                                            Text(example.note, fontSize = 12.sp, color = p.muted2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val forms = card.formEntries()
                if (forms.isNotEmpty()) {
                    DetailBlock("FORMS") { ChipRow(forms.map { "${it.first} ${it.second}" }) }
                }
                if (card.synonyms.isNotEmpty()) DetailBlock("SYNONYMS") { ChipRow(card.synonyms) }
                if (card.collocations.isNotEmpty()) DetailBlock("COLLOCATIONS") { ChipRow(card.collocations) }
                if (card.antonyms.isNotEmpty()) DetailBlock("ANTONYMS") { ChipRow(card.antonyms) }
                if (card.usageNotes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(p.surface3, RoundedCornerShape(10.dp))
                            .padding(11.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = null, tint = p.primary2, modifier = Modifier.size(16.dp))
                        Text(card.usageNotes, fontSize = 13.sp, lineHeight = 19.sp, color = p.muted)
                    }
                }
                DetailBlock("IMAGE") {
                    CardImageControls(
                        card = card,
                        busy = imageBusy,
                        onLink = onImageLink,
                        onUpload = onImageUpload,
                        onRemove = onImageRemove,
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
                // .card-actions
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (card.suspended) {
                        LexiButton("Unblock", kind = ButtonKind.Ghost, leadingIcon = Icons.Filled.LockOpen, enabled = !busy, onClick = onSuspendToggle)
                    } else {
                        LexiButton("Block", kind = ButtonKind.Ghost, leadingIcon = Icons.Filled.LockOpen, enabled = !busy, onClick = onSuspendToggle)
                    }
                    LexiButton(
                        "Pronounce",
                        kind = ButtonKind.Ghost,
                        leadingIcon = Icons.AutoMirrored.Filled.VolumeUp,
                        onClick = onPronounce,
                    )
                    LexiButton("Edit", kind = ButtonKind.Ghost, leadingIcon = Icons.Filled.Edit, onClick = onEdit)
                    LexiButton("Delete", kind = ButtonKind.DangerText, leadingIcon = Icons.Filled.Delete, enabled = !busy, onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun DetailBlock(label: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column {
        Text(
            label,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.W800,
            color = p.muted2,
        )
        Spacer(Modifier.height(7.dp))
        content()
    }
}
