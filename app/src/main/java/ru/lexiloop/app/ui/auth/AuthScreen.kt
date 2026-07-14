package ru.lexiloop.app.ui.auth

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.lexiloop.app.ui.components.Badge
import ru.lexiloop.app.ui.components.ButtonKind
import ru.lexiloop.app.ui.components.FormError
import ru.lexiloop.app.ui.components.LexiButton
import ru.lexiloop.app.ui.components.LexiTextField
import ru.lexiloop.app.ui.components.Spinner
import ru.lexiloop.app.ui.theme.LocalPalette
import ru.lexiloop.app.ui.theme.Manrope

@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val p = LocalPalette.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
    ) {
        // .auth-brand
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
            Spacer(Modifier.width(10.dp))
            Text("LexiLoop", fontFamily = Manrope, fontSize = 19.sp, fontWeight = FontWeight.W800, color = p.text)
        }

        Spacer(Modifier.height(28.dp))

        // .auth-copy (compact, like the ≤560px layout)
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Badge("AI-assisted learning", icon = Icons.Filled.AutoAwesome)
            Spacer(Modifier.height(12.dp))
            Text(
                buildAnnotatedString {
                    append("Remember words.\n")
                    withStyle(SpanStyle(color = p.primary2)) { append("Actually use them.") }
                },
                fontFamily = Manrope,
                fontSize = 30.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.W700,
                color = p.text,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(20.dp))

        // .auth-card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.surface, RoundedCornerShape(18.dp))
                .border(1.dp, p.border2, RoundedCornerShape(18.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // .auth-tabs
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(p.surface2, RoundedCornerShape(11.dp))
                    .padding(4.dp),
            ) {
                AuthTab("Sign in", active = !state.registerMode, modifier = Modifier.weight(1f)) {
                    if (state.registerMode) viewModel.toggleMode()
                }
                AuthTab("Create account", active = state.registerMode, modifier = Modifier.weight(1f)) {
                    if (!state.registerMode) viewModel.toggleMode()
                }
            }

            Column {
                Text(
                    if (state.registerMode) "Start your learning loop" else "Welcome back",
                    fontFamily = Manrope,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.W700,
                    color = p.text,
                )
                Spacer(Modifier.height(5.dp))
                Text("No email required. Your username is enough.", fontSize = 13.sp, color = p.muted)
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Username", fontSize = 12.sp, fontWeight = FontWeight.W600, color = p.muted)
                LexiTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    placeholder = "curious_fox",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Password", fontSize = 12.sp, fontWeight = FontWeight.W600, color = p.muted)
                LexiTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    placeholder = "At least 8 characters",
                    password = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                )
            }

            state.error?.let { FormError(it) }

            if (state.loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Spinner(size = 24)
                }
            } else {
                LexiButton(
                    if (state.registerMode) "Create account" else "Sign in",
                    onClick = viewModel::submit,
                    modifier = Modifier.fillMaxWidth(),
                    big = true,
                    enabled = state.canSubmit,
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                )
            }

            Text(
                "Provider tokens are encrypted before storage.",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 10.sp,
                color = p.muted2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AuthTab(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier = modifier
            .background(if (active) p.surface3 else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
            color = if (active) p.text else p.muted,
        )
    }
}
