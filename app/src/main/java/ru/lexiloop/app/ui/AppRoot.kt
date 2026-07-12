package ru.lexiloop.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.lexiloop.app.MainViewModel
import ru.lexiloop.app.data.auth.SessionState
import ru.lexiloop.app.ui.auth.AuthScreen
import ru.lexiloop.app.ui.library.LibraryScreen
import ru.lexiloop.app.ui.overview.OverviewScreen
import ru.lexiloop.app.ui.settings.SettingsScreen
import ru.lexiloop.app.ui.study.StudyScreen

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    Tab("overview", "Overview", Icons.Filled.Home),
    Tab("study", "Study", Icons.Filled.School),
    Tab("library", "Library", Icons.AutoMirrored.Filled.MenuBook),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun AppRoot(mainViewModel: MainViewModel = hiltViewModel()) {
    val session by mainViewModel.session.collectAsStateWithLifecycle()
    when (session) {
        is SessionState.Loading -> Unit // The system splash screen is still visible.
        is SessionState.LoggedOut -> AuthScreen()
        is SessionState.LoggedIn -> HomeScaffold()
    }
}

@Composable
private fun HomeScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "overview",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("overview") {
                OverviewScreen(onStartStudy = { navController.navigate("study") })
            }
            composable("study") { StudyScreen() }
            composable("library") { LibraryScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
