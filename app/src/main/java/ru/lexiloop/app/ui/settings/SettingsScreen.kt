package ru.lexiloop.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.lexiloop.app.BuildConfig
import ru.lexiloop.app.ui.common.ErrorBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { ErrorBanner(it) }

            state.me?.let { me ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Account", style = MaterialTheme.typography.titleMedium)
                        InfoRow("Username", me.username)
                        InfoRow("Server", BuildConfig.API_BASE_URL)
                    }
                }
                me.settings?.let { profile ->
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Study preferences", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Managed on the LexiLoop website — the app follows them automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            HorizontalDivider()
                            InfoRow(
                                "Task types",
                                profile.studyDirections
                                    .joinToString(", ") { directionLabel(it) }
                                    .ifEmpty { "Default" },
                            )
                            InfoRow("Daily new cards", profile.dailyNewLimit.toString())
                            if (profile.generationModel.isNotEmpty()) {
                                InfoRow("Generation model", profile.generationModel)
                            }
                            if (profile.judgeModel.isNotEmpty()) {
                                InfoRow("Judge model", profile.judgeModel)
                            }
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    InfoRow("App version", BuildConfig.VERSION_NAME)
                }
            }

            OutlinedButton(
                onClick = viewModel::logout,
                enabled = !state.loggingOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.loggingOut) "Signing out…" else "Sign out")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun directionLabel(direction: String): String = when (direction) {
    "term_to_definition" -> "Word → definition"
    "definition_to_term" -> "Definition → word"
    "term_to_sentence" -> "Word → sentence"
    "mixed" -> "Mixed"
    else -> direction
}
