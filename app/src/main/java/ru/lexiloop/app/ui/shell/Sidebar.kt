package ru.lexiloop.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.PoolDto
import ru.lexiloop.app.data.api.PoolWriteBody
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.ContentRepository
import ru.lexiloop.app.ui.components.ButtonKind
import ru.lexiloop.app.ui.components.FieldLabel
import ru.lexiloop.app.ui.components.FormError
import ru.lexiloop.app.ui.components.LexiButton
import ru.lexiloop.app.ui.components.LexiModal
import ru.lexiloop.app.ui.components.LexiSelect
import ru.lexiloop.app.ui.components.LexiTextArea
import ru.lexiloop.app.ui.components.LexiTextField
import ru.lexiloop.app.ui.components.ModalActions
import ru.lexiloop.app.ui.components.PoolDot
import ru.lexiloop.app.ui.theme.LocalPalette
import ru.lexiloop.app.ui.theme.Manrope
import ru.lexiloop.app.ui.theme.POOL_ACCENTS
import ru.lexiloop.app.ui.theme.poolAccentColor

/** The site's sidebar, rendered as the drawer content on mobile. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarContent(
    viewModel: ShellViewModel,
    repository: ContentRepository,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    val page by viewModel.page.collectAsStateWithLifecycle()
    val pools by viewModel.pools.collectAsStateWithLifecycle()
    val activePoolId by viewModel.activePoolId.collectAsStateWithLifecycle()
    val shellState by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var menuPool by remember { mutableStateOf<PoolDto?>(null) }
    var editing by remember { mutableStateOf<PoolDto?>(null) }
    var deleting by remember { mutableStateOf<PoolDto?>(null) }
    var transfer by remember { mutableStateOf<Pair<PoolDto, String>?>(null) } // pool to mode

    val effectiveDark = when (settings.theme) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 22.dp),
    ) {
        // Brand row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(
                        Brush.linearGradient(listOf(p.primary, p.primaryDeep)),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("LexiLoop", fontFamily = Manrope, fontSize = 19.sp, fontWeight = FontWeight.W800, color = p.text)
                Text("Vocabulary studio", fontSize = 12.sp, color = p.muted)
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close navigation", tint = p.muted)
            }
        }
        Spacer(Modifier.height(24.dp))

        // Main navigation
        val dueTotal = pools.sumOf { it.dueCount }
        NavLink(Icons.Filled.Speed, "Overview", page == LexiPage.Home) { viewModel.navigate(LexiPage.Home); onClose() }
        NavLink(Icons.Filled.Psychology, "Study", page == LexiPage.Study, badge = dueTotal.takeIf { it > 0 }) {
            viewModel.navigate(LexiPage.Study); onClose()
        }
        NavLink(Icons.AutoMirrored.Filled.MenuBook, "Library", page == LexiPage.Library) { viewModel.navigate(LexiPage.Library); onClose() }
        NavLink(Icons.Filled.Paid, "AI usage", page == LexiPage.Analytics) { viewModel.navigate(LexiPage.Analytics); onClose() }

        // Pools section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 11.dp, end = 4.dp, top = 27.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "YOUR POOLS",
                modifier = Modifier.weight(1f),
                fontSize = 11.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = 1.3.sp,
                color = p.muted2,
            )
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { creating = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New pool", tint = p.muted, modifier = Modifier.size(18.dp))
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items(pools, key = { it.id }) { pool ->
                val active = pool.id == activePoolId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) p.surface2 else Color.Transparent)
                        .combinedClickable(
                            onClick = { viewModel.selectPool(pool.id); onClose() },
                            onLongClick = { menuPool = pool },
                        )
                        .height(48.dp)
                        .padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PoolDot(poolAccentColor(pool.accent, p))
                    Text(
                        pool.name,
                        modifier = Modifier.weight(1f),
                        color = if (active) p.text else p.muted,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(pool.cardCount.toString(), fontSize = 13.sp, color = p.muted2)
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { menuPool = pool },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Actions for ${pool.name}", tint = p.muted2, modifier = Modifier.size(17.dp))
                    }
                }
            }
            if (pools.isEmpty()) {
                item {
                    Text("Create a pool to begin.", fontSize = 14.sp, color = p.muted2, modifier = Modifier.padding(12.dp))
                }
            }
        }

        // Bottom block
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Spacer(Modifier.height(12.dp))
        NavLink(
            if (effectiveDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
            if (effectiveDark) "Dark mode" else "Light mode",
            active = false,
            iconTint = p.primary2,
        ) { viewModel.toggleTheme(systemDark) }
        NavLink(Icons.Filled.Settings, "Settings", page == LexiPage.Settings) { viewModel.navigate(LexiPage.Settings); onClose() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(p.surface3, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    shellState.username.take(2).uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W800,
                    color = p.text,
                )
            }
            Text(
                shellState.username,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.W600,
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.signOut() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = p.muted, modifier = Modifier.size(19.dp))
            }
        }
    }

    // --- Pool action menu + modals ---

    menuPool?.let { pool ->
        LexiModal(title = pool.name, subtitle = "${pool.cardCount} cards", onClose = { menuPool = null }) {
            PoolMenuItem(Icons.Filled.Edit, "Rename or change color") { editing = pool; menuPool = null }
            PoolMenuItem(Icons.Filled.ContentCopy, "Copy into another pool") { transfer = pool to "copy"; menuPool = null }
            PoolMenuItem(Icons.Filled.CallMerge, "Merge and remove source") { transfer = pool to "move"; menuPool = null }
            PoolMenuItem(Icons.Filled.Delete, "Delete pool", danger = true) { deleting = pool; menuPool = null }
        }
    }

    if (creating) {
        PoolFormModal(
            title = "Create a new pool",
            initial = null,
            onClose = { creating = false },
            onSubmit = { name, description, _ ->
                when (val result = repository.createPool(name, description)) {
                    is ApiResult.Success -> {
                        creating = false
                        viewModel.refreshShell()
                        viewModel.selectPool(result.data.id)
                        null
                    }
                    is ApiResult.Error -> result.message
                }
            },
        )
    }

    editing?.let { pool ->
        PoolFormModal(
            title = "Edit pool",
            subtitle = "Rename this collection or give it a more recognizable color.",
            initial = pool,
            onClose = { editing = null },
            onSubmit = { name, description, accent ->
                when (val result = repository.patchPool(pool.id, PoolWriteBody(name = name, description = description, accent = accent))) {
                    is ApiResult.Success -> {
                        editing = null
                        viewModel.refreshShell()
                        viewModel.toastBus.success("Pool updated")
                        null
                    }
                    is ApiResult.Error -> result.message
                }
            },
        )
    }

    deleting?.let { pool ->
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        LexiModal(
            title = "Delete “${pool.name}”?",
            subtitle = "This permanently deletes every card and schedule in this pool. Historical AI spend remains in analytics under Deleted pools.",
            onClose = { deleting = null },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.surface2, RoundedCornerShape(12.dp))
                    .border(1.dp, p.border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = p.red, modifier = Modifier.size(25.dp))
                Column {
                    Text("${pool.cardCount} cards will be deleted", fontSize = 14.sp, fontWeight = FontWeight.W700, color = p.text)
                    Text("This action cannot be undone.", fontSize = 11.sp, color = p.muted)
                }
            }
            error?.let { FormError(it) }
            ModalActions {
                LexiButton("Cancel", kind = ButtonKind.Ghost, onClick = { deleting = null })
                LexiButton(if (busy) "Deleting…" else "Delete pool", kind = ButtonKind.Danger, enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        when (val result = repository.deletePool(pool.id)) {
                            is ApiResult.Success -> {
                                deleting = null
                                viewModel.refreshShell()
                                viewModel.toastBus.success("Deleted “${pool.name}”")
                            }
                            is ApiResult.Error -> {
                                busy = false
                                error = result.message
                            }
                        }
                    }
                })
            }
        }
    }

    transfer?.let { (source, mode) ->
        val targets = pools.filter { it.id != source.id }
        var target by remember(source.id) { mutableStateOf(targets.firstOrNull()?.id ?: 0) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        LexiModal(
            title = if (mode == "copy") "Copy cards into another pool" else "Merge pools",
            subtitle = if (mode == "copy") {
                "The source pool remains unchanged. Cards and current progress are copied; AI usage and review history are not duplicated."
            } else {
                "The destination keeps its name. Cards, review history, and historical AI usage move there. The source pool is deleted."
            },
            onClose = { transfer = null },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.surface2, RoundedCornerShape(12.dp))
                    .border(1.dp, p.border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PoolDot(poolAccentColor(source.accent, p), size = 14)
                Column {
                    Text("Source pool", fontSize = 11.sp, color = p.muted)
                    Text(source.name, fontSize = 14.sp, fontWeight = FontWeight.W700, color = p.text)
                    Text("${source.cardCount} cards", fontSize = 11.sp, color = p.muted)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                FieldLabel("Destination pool")
                LexiSelect(
                    value = target.toString(),
                    options = targets.map { it.id.toString() to "${it.name} · ${it.cardCount} cards" },
                    onSelect = { target = it.toIntOrNull() ?: 0 },
                )
            }
            if (mode == "move") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(p.orange.copy(alpha = 0.10f), RoundedCornerShape(11.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = p.orange, modifier = Modifier.size(17.dp))
                    Text(
                        "Duplicate words are consolidated. The more mature schedule is retained and the earlier due date wins.",
                        fontSize = 12.sp,
                        color = p.orange,
                        lineHeight = 17.sp,
                    )
                }
            }
            if (targets.isEmpty()) FormError("Create another pool first.")
            error?.let { FormError(it) }
            ModalActions {
                LexiButton("Cancel", kind = ButtonKind.Ghost, onClick = { transfer = null })
                LexiButton(
                    if (busy) "Working…" else if (mode == "copy") "Copy cards" else "Merge pools",
                    enabled = !busy && targets.isNotEmpty(),
                    onClick = {
                        busy = true
                        scope.launch {
                            when (val result = repository.transferPool(source.id, target, mode)) {
                                is ApiResult.Success -> {
                                    transfer = null
                                    viewModel.selectPool(target)
                                    viewModel.refreshShell()
                                    viewModel.toastBus.success(
                                        if (mode == "copy") "Cards copied without changing the source pool." else "Pools merged.",
                                    )
                                }
                                is ApiResult.Error -> {
                                    busy = false
                                    error = result.message
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NavLink(
    icon: ImageVector,
    label: String,
    active: Boolean,
    badge: Int? = null,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) p.primaryBg else Color.Transparent)
            .clickable(onClick = onClick)
            .height(52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint ?: if (active) p.primary2 else p.muted,
            modifier = Modifier.size(21.dp),
        )
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (active) p.text else p.muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.W600,
        )
        if (badge != null) {
            Box(
                Modifier
                    .background(p.surface3, RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge.toString(), fontSize = 12.sp, fontWeight = FontWeight.W700, color = p.text)
            }
        }
    }
}

@Composable
private fun PoolMenuItem(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) p.red else p.text, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 14.sp, color = if (danger) p.red else p.text)
    }
}

@Composable
private fun PoolFormModal(
    title: String,
    initial: PoolDto?,
    onClose: () -> Unit,
    onSubmit: suspend (name: String, description: String, accent: String?) -> String?,
    subtitle: String? = null,
) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var accent by remember { mutableStateOf(initial?.accent?.takeIf { it in POOL_ACCENTS } ?: "emerald") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LexiModal(title = title, subtitle = subtitle, onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            FieldLabel("Pool name")
            LexiTextField(value = name, onValueChange = { name = it }, placeholder = "Business English")
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            FieldLabel("Description")
            LexiTextArea(value = description, onValueChange = { description = it }, placeholder = "Optional learning goal")
        }
        if (initial != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.surface2, RoundedCornerShape(12.dp))
                    .border(1.dp, p.border, RoundedCornerShape(12.dp))
                    .padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "POOL COLOR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 1.sp,
                    color = p.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    POOL_ACCENTS.forEach { (accentName, color) ->
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(p.surface, RoundedCornerShape(10.dp))
                                .border(
                                    width = if (accent == accentName) 2.dp else 1.dp,
                                    color = if (accent == accentName) color else p.border2,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { accent = accentName },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(20.dp).background(color, RoundedCornerShape(6.dp)))
                        }
                    }
                }
            }
        }
        error?.let { FormError(it) }
        ModalActions {
            LexiButton("Cancel", kind = ButtonKind.Ghost, onClick = onClose)
            LexiButton(
                if (busy) "Saving…" else if (initial == null) "Create pool" else "Save pool",
                enabled = !busy && name.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val failure = onSubmit(name.trim(), description, if (initial != null) accent else null)
                        if (failure != null) {
                            busy = false
                            error = failure
                        }
                    }
                },
            )
        }
    }
}
