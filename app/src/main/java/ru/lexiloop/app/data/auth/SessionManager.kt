package ru.lexiloop.app.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.lexiloop.app.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val username: String) : SessionState
}

/**
 * Single source of truth for the auth session. The token is persisted in
 * DataStore (excluded from cloud backups) and mirrored into a volatile field
 * so the OkHttp interceptor can read it without suspending.
 */
@Singleton
class SessionManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    @Volatile
    var currentToken: String? = null
        private set

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state

    init {
        scope.launch {
            val prefs = dataStore.data.first()
            applySession(prefs[KEY_TOKEN], prefs[KEY_USERNAME])
        }
    }

    suspend fun save(token: String, username: String) {
        dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USERNAME] = username
        }
        applySession(token, username)
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_USERNAME)
        }
        applySession(null, null)
    }

    private fun applySession(token: String?, username: String?) {
        currentToken = token
        _state.value = if (token.isNullOrEmpty()) {
            SessionState.LoggedOut
        } else {
            SessionState.LoggedIn(username.orEmpty())
        }
    }

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_USERNAME = stringPreferencesKey("username")
    }
}
