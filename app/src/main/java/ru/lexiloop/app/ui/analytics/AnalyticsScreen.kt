package ru.lexiloop.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.AnalyticsResponse
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.ContentRepository
import ru.lexiloop.app.data.repo.ToastBus
import ru.lexiloop.app.ui.components.LoaderView
import ru.lexiloop.app.ui.components.Panel
import ru.lexiloop.app.ui.components.PoolDot
import ru.lexiloop.app.ui.components.SectionHeading
import ru.lexiloop.app.ui.components.StatCard
import ru.lexiloop.app.ui.pagePadding
import ru.lexiloop.app.ui.theme.LocalPalette
import ru.lexiloop.app.ui.theme.Manrope
import ru.lexiloop.app.ui.theme.poolAccentColor
import javax.inject.Inject

data class AnalyticsUiState(
    val loading: Boolean = true,
    val data: AnalyticsResponse? = null,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val toastBus: ToastBus,
) : ViewModel() {
    private val _state = MutableStateFlow(AnalyticsUiState())
    val state: StateFlow<AnalyticsUiState> = _state

    init {
        viewModelScope.launch {
            when (val result = repository.analytics()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, data = result.data) }
                is ApiResult.Error -> {
                    _state.update { it.copy(loading = false) }
                    toastBus.error(result.message)
                }
            }
        }
    }
}

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val p = LocalPalette.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoaderView("Loading cost ledger…")
        }
        return
    }
    val data = state.data ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "stats") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        Icons.Filled.Paid,
                        "$${"%.4f".format(data.totals.cost)}", "Recorded cost", "reported by router adapters",
                        Modifier.weight(1f),
                        accent = true,
                    )
                    StatCard(
                        Icons.Filled.AutoAwesome,
                        "${data.totals.calls}", "LLM calls", "generation + judging",
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        Icons.Filled.Bolt,
                        formatTokens(data.totals.tokens), "Total tokens", "when providers report usage",
                        Modifier.weight(1f),
                    )
                    StatCard(
                        Icons.Filled.Schedule,
                        "${data.totals.averageLatency}s", "Average latency", "latest 500 calls",
                        Modifier.weight(1f),
                    )
                }
            }
        }

        item(key = "chart") {
            Panel {
                SectionHeading("Cost over time", "Provider spend")
                Spacer(Modifier.height(16.dp))
                if (data.daily.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("The chart appears after your first LLM call.", fontSize = 14.sp, color = p.muted)
                    }
                } else {
                    CostChart(data)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(data.daily.first().day, fontSize = 11.sp, color = p.muted2, modifier = Modifier.weight(1f))
                        Text(data.daily.last().day, fontSize = 11.sp, color = p.muted2)
                    }
                }
            }
        }

        item(key = "by-pool") {
            Panel {
                SectionHeading("Breakdown", "By pool")
                Spacer(Modifier.height(10.dp))
                if (data.byPool.isEmpty()) {
                    Text("No pools yet.", fontSize = 14.sp, color = p.muted)
                }
                data.byPool.forEachIndexed { index, row ->
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        PoolDot(poolAccentColor(row.accent, p))
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.poolName.ifEmpty { "Deleted pool" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W700,
                                color = p.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${row.calls} calls · ${formatTokens(row.tokens)} tokens",
                                fontSize = 11.sp,
                                color = p.muted,
                            )
                        }
                        Text(
                            "$${"%.4f".format(row.cost)}",
                            fontFamily = Manrope,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W700,
                            color = p.text,
                        )
                    }
                }
            }
        }

        item(key = "failures") {
            Panel {
                SectionHeading("Health", "Recent failures")
                Spacer(Modifier.height(10.dp))
                if (data.failures.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = p.green, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No recent failures", fontSize = 15.sp, fontWeight = FontWeight.W700, color = p.text)
                        Spacer(Modifier.height(4.dp))
                        Text("Your provider calls look healthy.", fontSize = 12.sp, color = p.green)
                    }
                }
                data.failures.forEachIndexed { index, failure ->
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Box(
                            Modifier
                                .size(25.dp)
                                .background(p.red.copy(alpha = 0.12f), RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("!", fontSize = 14.sp, fontWeight = FontWeight.W800, color = p.red)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${failure.operation} · ${failure.model}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.W700,
                                color = p.text,
                            )
                            Text(
                                failure.error,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = p.muted,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(failure.createdAt.take(19).replace('T', ' '), fontSize = 10.sp, color = p.muted2)
                        }
                    }
                }
            }
        }
    }
}

/** The `.chart-wrap` daily-cost line chart with an area gradient. */
@Composable
private fun CostChart(data: AnalyticsResponse) {
    val p = LocalPalette.current
    val rows = data.daily
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        val padX = 8.dp.toPx()
        val padY = 12.dp.toPx()
        val w = size.width - padX * 2
        val h = size.height - padY * 2
        val maxCost = maxOf(rows.maxOf { it.cost }, 1e-6)

        // Gridlines
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
            val y = padY + fraction * h
            drawLine(p.border, Offset(padX, y), Offset(padX + w, y), strokeWidth = 1.dp.toPx())
        }

        val points = rows.mapIndexed { index, row ->
            Offset(
                x = padX + if (rows.size == 1) w / 2 else index * w / (rows.size - 1),
                y = padY + h - (row.cost / maxCost).toFloat() * h,
            )
        }
        val line = Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, padY + h)
            lineTo(points.first().x, padY + h)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                listOf(p.primary.copy(alpha = 0.28f), p.primary.copy(alpha = 0f)),
                startY = padY,
                endY = padY + h,
            ),
        )
        drawPath(line, p.primary, style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        points.forEach { point ->
            drawCircle(p.surface, radius = 4.dp.toPx(), center = point)
            drawCircle(p.primary, radius = 4.dp.toPx(), center = point, style = Stroke(width = 3.dp.toPx()))
        }
    }
}

private fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
    tokens >= 10_000 -> "%.0fk".format(tokens / 1_000.0)
    else -> tokens.toString()
}
