package ru.lexiloop.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.auth.SessionState
import ru.lexiloop.app.ui.auth.AuthScreen
import ru.lexiloop.app.ui.analytics.AnalyticsScreen
import ru.lexiloop.app.ui.components.LexiIconButton
import ru.lexiloop.app.ui.library.LibraryScreen
import ru.lexiloop.app.ui.overview.OverviewScreen
import ru.lexiloop.app.ui.settings.SettingsScreen
import ru.lexiloop.app.ui.shell.LexiPage
import ru.lexiloop.app.ui.shell.ShellViewModel
import ru.lexiloop.app.ui.shell.SidebarContent
import ru.lexiloop.app.ui.study.StudyScreen
import ru.lexiloop.app.ui.theme.LexiTheme
import ru.lexiloop.app.ui.theme.LocalPalette

@Composable
fun AppRoot(shell: ShellViewModel = hiltViewModel()) {
    val session by shell.session.collectAsStateWithLifecycle()
    val settings by shell.settings.collectAsStateWithLifecycle()
    val fontScale by shell.fontScale.collectAsStateWithLifecycle()
    val deviceAccent by shell.deviceAccent.collectAsStateWithLifecycle()

    LexiTheme(
        theme = settings.theme,
        // The device's own color once picked in the app; the site keeps its own.
        accent = deviceAccent ?: settings.accentColor,
        fontScale = fontScale,
    ) {
        when (session) {
            is SessionState.Loading -> Unit // system splash is still on screen
            is SessionState.LoggedOut -> AuthScreen()
            is SessionState.LoggedIn -> AppShell(shell)
        }
    }
}

/** Content padding replicating `.page` (with the mobile 74px top offset). */
@Composable
fun pagePadding(horizontal: Int = 14): PaddingValues {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(
        start = horizontal.dp,
        end = horizontal.dp,
        top = statusBar + 68.dp,
        bottom = navBar + 45.dp,
    )
}

@Composable
private fun AppShell(shell: ShellViewModel) {
    val p = LocalPalette.current
    val page by shell.page.collectAsStateWithLifecycle()
    val toast by shell.toast.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.isOpen || page != LexiPage.Home) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            else -> shell.navigate(LexiPage.Home)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.92f),
                drawerShape = RectangleShape,
                drawerContainerColor = p.surface,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                SidebarContent(
                    viewModel = shell,
                    repository = shell.repository,
                    onClose = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(p.bg),
        ) {
            when (page) {
                LexiPage.Home -> OverviewScreen()
                LexiPage.Study -> StudyScreen()
                LexiPage.Library -> LibraryScreen()
                LexiPage.Analytics -> AnalyticsScreen()
                LexiPage.Settings -> SettingsScreen()
            }

            // .floating-sidebar-button
            Box(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 14.dp, top = 8.dp),
            ) {
                LexiIconButton(
                    Icons.Filled.Menu,
                    contentDescription = "Open navigation",
                    onClick = { scope.launch { drawerState.open() } },
                )
            }

            // .toast
            toast?.let { event ->
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Above the soft keyboard when one is open, else above
                        // the navigation bar.
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
                        .background(p.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, p.border2, RoundedCornerShape(12.dp))
                        .padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (event.isError) Icons.Filled.ErrorOutline else Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (event.isError) p.red else p.green,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        event.message,
                        modifier = Modifier.padding(start = 9.dp),
                        fontSize = 13.sp,
                        color = p.text,
                    )
                }
            }
        }
    }
}
