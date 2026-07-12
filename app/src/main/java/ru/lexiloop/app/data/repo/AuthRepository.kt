package ru.lexiloop.app.data.repo

import ru.lexiloop.app.data.api.AuthRequest
import ru.lexiloop.app.data.api.LexiLoopApi
import ru.lexiloop.app.data.auth.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: LexiLoopApi,
    private val sessionManager: SessionManager,
) {
    suspend fun login(username: String, password: String): ApiResult<Unit> =
        safeApi { api.login(AuthRequest(username, password)) }
            .also { result ->
                if (result is ApiResult.Success) {
                    sessionManager.save(result.data.token, result.data.username)
                }
            }
            .map { }

    suspend fun register(username: String, password: String): ApiResult<Unit> =
        safeApi { api.register(AuthRequest(username, password)) }
            .also { result ->
                if (result is ApiResult.Success) {
                    sessionManager.save(result.data.token, result.data.username)
                }
            }
            .map { }

    suspend fun logout() {
        // Best effort: drop the server-side token, but always clear locally.
        safeApi { api.logout() }
        sessionManager.clear()
    }
}
