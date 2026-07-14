package ru.lexiloop.app.ui.overview

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.lexiloop.app.data.api.ActivityDayDto
import ru.lexiloop.app.ui.components.Badge
import ru.lexiloop.app.ui.components.ButtonKind
import ru.lexiloop.app.ui.components.EmptyState
import ru.lexiloop.app.ui.components.LexiButton
import ru.lexiloop.app.ui.components.LoaderView
import ru.lexiloop.app.ui.components.Panel
import ru.lexiloop.app.ui.components.SectionHeading
import ru.lexiloop.app.ui.components.StatCard
import ru.lexiloop.app.ui.pagePadding
import ru.lexiloop.app.ui.shell.LexiPage
import ru.lexiloop.app.ui.shell.ShellViewModel
import ru.lexiloop.app.ui.theme.LocalPalette
import ru.lexiloop.app.ui.theme.Manrope
import ru.lexiloop.app.ui.theme.poolAccentColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = hiltViewModel(),
    shell: ShellViewModel = hiltViewModel(),
) {
    val p = LocalPalette.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pools by viewModel.pools.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoaderView("Loading your overview…")
        }
        return
    }
    val stats = state.stats

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // .hero-panel
        item(key = "hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(21.dp))
                    .background(
                        Brush.linearGradient(listOf(p.surface, p.surface2)),
                        RoundedCornerShape(21.dp),
                    )
                    .border(1.dp, p.border, RoundedCornerShape(21.dp))
                    .padding(horizontal = 20.dp, vertical = 27.dp),
            ) {
                Badge("${stats?.streak ?: 0} day streak", icon = Icons.Filled.LocalFireDepartment)
                Spacer(Modifier.height(18.dp))
                Text(
                    "Build a vocabulary that",
                    fontFamily = Manrope,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.W700,
                    color = p.text,
                )
                Text(
                    "stays with you.",
                    fontFamily = Manrope,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.W700,
                    color = p.primary2,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    if ((stats?.dueNow ?: 0) > 0) {
                        "${stats?.dueNow} cards are ready for review."
                    } else {
                        "You are caught up. Add a few words or explore your library."
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = p.muted,
                )
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LexiButton(
                        "Start studying",
                        big = true,
                        leadingIcon = Icons.Filled.Psychology,
                        onClick = { shell.navigate(LexiPage.Study) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LexiButton(
                        "Add words",
                        kind = ButtonKind.Secondary,
                        big = true,
                        leadingIcon = Icons.Filled.Add,
                        onClick = { shell.navigate(LexiPage.Library) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // .stat-grid (2 columns on mobile)
        item(key = "stats") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        Icons.AutoMirrored.Filled.MenuBook,
                        "${stats?.totalCards ?: 0}", "Total cards", "across all pools",
                        Modifier.weight(1f),
                    )
                    StatCard(
                        Icons.Filled.Schedule,
                        "${stats?.dueNow ?: 0}", "Due now", "ready to review",
                        Modifier.weight(1f),
                        accent = true,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        Icons.Filled.Bolt,
                        "${stats?.reviewsToday ?: 0}", "Reviews today", "keep the loop moving",
                        Modifier.weight(1f),
                    )
                    StatCard(
                        Icons.Filled.VerifiedUser,
                        "${stats?.retention ?: 0.0}%", "Answer retention", "all-time accepted",
                        Modifier.weight(1f),
                    )
                }
            }
        }

        // Retention ring (the hero visual, kept as its own compact panel)
        item(key = "ring") {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RetentionRing(
                        percent = (stats?.retention ?: 0.0).toFloat().coerceIn(0f, 100f),
                    )
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text("Answer retention", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Share of all reviews accepted as correct.",
                            fontSize = 13.sp,
                            color = p.muted,
                        )
                    }
                }
            }
        }

        // .activity-panel — study activity heatmap (vertical mobile variant)
        item(key = "activity") {
            Panel(padding = 17) {
                SectionHeading(
                    "Study activity", "Your learning year",
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    "${stats?.activity?.sumOf { it.reviews } ?: 0} reviews in the last year",
                    fontSize = 14.sp,
                    color = p.muted,
                )
                Spacer(Modifier.height(16.dp))
                ActivityHeatmap(stats?.activity ?: emptyList())
            }
        }

        // COLLECTIONS
        item(key = "pools-heading") {
            SectionHeading("Collections", "Your pools") {
                Row(
                    modifier = Modifier.clickable { shell.navigate(LexiPage.Library) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Open library", color = p.primary2, fontSize = 14.sp, fontWeight = FontWeight.W700)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = p.primary2, modifier = Modifier.size(16.dp))
                }
            }
        }

        items(pools, key = { it.id }) { pool ->
            // .pool-card (mobile compact grid layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(p.surface, RoundedCornerShape(18.dp))
                    .border(1.dp, p.border, RoundedCornerShape(18.dp))
                    .clickable {
                        viewModel.selectPool(pool.id)
                        shell.navigate(LexiPage.Library)
                    }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(poolAccentColor(pool.accent, p), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        pool.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W700,
                        fontFamily = Manrope,
                        color = p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pool.description.ifEmpty { "Your vocabulary collection" },
                        fontSize = 12.sp,
                        color = p.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        Text("${pool.cardCount} cards", fontSize = 11.sp, color = p.muted)
                        Text(
                            "${pool.dueCount} due",
                            fontSize = 11.sp,
                            color = if (pool.dueCount > 0) p.orange else p.muted,
                        )
                    }
                }
            }
        }

        if (pools.isEmpty()) {
            item(key = "no-pools") {
                EmptyState(
                    icon = Icons.Filled.Add,
                    title = "No pools yet",
                    text = "Create your first pool from the sidebar, then add a word and let AI fill in the rest.",
                )
            }
        }
    }
}

/** The hero `.ring`: conic progress with the retention percentage inside. */
@Composable
private fun RetentionRing(percent: Float) {
    val p = LocalPalette.current
    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 11.dp.toPx())
            val inset = 11.dp.toPx() / 2
            drawArc(
                color = p.surface3,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            )
            drawArc(
                color = p.primary,
                startAngle = -90f,
                sweepAngle = 360f * (percent / 100f),
                useCenter = false,
                style = stroke,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${percent.toInt()}%",
                fontFamily = Manrope,
                fontSize = 24.sp,
                fontWeight = FontWeight.W800,
                color = p.text,
            )
            Text("retention", fontSize = 12.sp, color = p.muted)
        }
    }
}

private const val RECENT_WEEKS = 13

/**
 * The v1.12 vertical activity heatmap: weeks as rows, most recent first,
 * absolute thresholds (1 / 10 / 50 / 120 reviews).
 */
@Composable
fun ActivityHeatmap(rows: List<ActivityDayDto>) {
    val p = LocalPalette.current
    var expanded by remember { mutableStateOf(false) }
    val byDay = remember(rows) { rows.associate { it.day to it.reviews } }

    val weeks = remember(byDay) {
        val today = LocalDate.now()
        var start = today.minusDays(364)
        start = start.minusDays(((start.dayOfWeek.value - 1).toLong())) // back to Monday
        val allWeeks = mutableListOf<List<Pair<LocalDate, Int>>>()
        var current = mutableListOf<Pair<LocalDate, Int>>()
        var day = start
        while (!day.isAfter(today)) {
            if (current.size == 7) {
                allWeeks.add(current)
                current = mutableListOf()
            }
            current.add(day to (byDay[day.toString()] ?: 0))
            day = day.plusDays(1)
        }
        if (current.isNotEmpty()) allWeeks.add(current)
        allWeeks.reversed()
    }

    fun level(count: Int): Int = when {
        count >= 120 -> 4
        count >= 50 -> 3
        count >= 10 -> 2
        count >= 1 -> 1
        else -> 0
    }

    fun cellColor(level: Int): Color = when (level) {
        0 -> p.surface3
        1 -> lerpColor(p.surface3, p.primary, 0.25f)
        2 -> lerpColor(p.surface3, p.primary, 0.50f)
        3 -> lerpColor(p.surface3, p.primary, 0.75f)
        else -> p.primary
    }

    val shown = if (expanded) weeks else weeks.take(RECENT_WEEKS)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Weekday header
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(40.dp))
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.W800, color = p.muted2)
                }
            }
        }
        var lastLabel = ""
        shown.forEachIndexed { index, week ->
            val monthStart = week.firstOrNull { it.first.dayOfMonth == 1 }
            var label = when {
                monthStart != null -> monthStart.first.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                index == 0 -> week.last().first.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                else -> ""
            }
            if (label.isNotEmpty() && label == lastLabel) label = ""
            if (label.isNotEmpty()) lastLabel = label
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(label, fontSize = 10.sp, color = p.muted, modifier = Modifier.padding(end = 6.dp))
                }
                week.forEach { (_, count) ->
                    Box(
                        Modifier
                            .size(15.dp)
                            .background(cellColor(level(count)), RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (expanded) "Show recent weeks" else "Show the full year",
                modifier = Modifier
                    .weight(1f)
                    .clickable { expanded = !expanded },
                color = p.primary2,
                fontSize = 12.sp,
                fontWeight = FontWeight.W700,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Less", fontSize = 10.sp, color = p.muted2)
                (0..4).forEach { levelValue ->
                    Box(
                        Modifier
                            .size(11.dp)
                            .background(cellColor(levelValue), RoundedCornerShape(3.dp)),
                    )
                }
                Text("More", fontSize = 10.sp, color = p.muted2)
            }
        }
    }
}

private fun lerpColor(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = 1f,
)
