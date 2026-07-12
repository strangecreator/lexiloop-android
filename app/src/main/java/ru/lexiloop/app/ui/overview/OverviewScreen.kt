package ru.lexiloop.app.ui.overview

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.lexiloop.app.data.api.PoolDto
import ru.lexiloop.app.ui.common.ErrorBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onStartStudy: () -> Unit,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Overview") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { error ->
                item(key = "error") { ErrorBanner(error) }
            }

            state.stats?.let { stats ->
                item(key = "stats") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Due now", stats.dueNow.toString(), Modifier.weight(1f))
                            StatCard("Today", stats.reviewsToday.toString(), Modifier.weight(1f))
                            StatCard("Streak", "${stats.streak}d", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Cards", stats.totalCards.toString(), Modifier.weight(1f))
                            StatCard("New", stats.newCards.toString(), Modifier.weight(1f))
                            StatCard("Retention", "${stats.retention}%", Modifier.weight(1f))
                        }
                    }
                }
                item(key = "start") {
                    FilledTonalButton(
                        onClick = onStartStudy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (stats.dueNow > 0) "Study ${stats.dueNow} due cards" else "Practice")
                    }
                }
            }

            item(key = "pools-header") {
                Spacer(Modifier.height(4.dp))
                Text("Your pools", style = MaterialTheme.typography.titleLarge)
            }

            item(key = "all-pools") {
                PoolRow(
                    name = "All pools",
                    subtitle = "Study across every pool",
                    accent = null,
                    dueCount = null,
                    selected = state.selectedPoolId == null,
                    onClick = { viewModel.selectPool(null) },
                )
            }

            items(state.pools, key = { it.id }) { pool ->
                PoolRow(
                    name = pool.name,
                    subtitle = "${pool.cardCount} cards",
                    accent = pool.accent,
                    dueCount = pool.dueCount,
                    selected = state.selectedPoolId == pool.id,
                    onClick = { viewModel.selectPool(pool) },
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun PoolRow(
    name: String,
    subtitle: String,
    accent: String?,
    dueCount: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            accent?.let { value ->
                parseAccent(value)?.let { color ->
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(color, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (dueCount != null && dueCount > 0) {
                Text(
                    "$dueCount due",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun parseAccent(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6) return null
    return try {
        Color(0xFF000000 or hex.toLong(16))
    } catch (_: NumberFormatException) {
        null
    }
}
