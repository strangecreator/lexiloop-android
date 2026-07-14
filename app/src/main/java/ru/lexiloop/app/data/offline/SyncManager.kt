package ru.lexiloop.app.data.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.api.LexiLoopApi
import ru.lexiloop.app.data.auth.SessionManager
import ru.lexiloop.app.data.auth.SessionState
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.PoolStore
import ru.lexiloop.app.data.repo.SettingsStore
import ru.lexiloop.app.data.repo.ToastBus
import ru.lexiloop.app.data.repo.safeApi
import ru.lexiloop.app.di.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replays work recorded while offline and keeps the offline cache warm.
 *
 * Conflict policy: pending reviews replay in the order they were made and the
 * server's scheduler applies them at sync time; if the server rejects one
 * (the card was deleted, or its state moved on), that event is dropped — the
 * server always wins. Offline settings edits merge field-by-field with the
 * last write winning.
 */
@Singleton
class SyncManager @Inject constructor(
    private val api: LexiLoopApi,
    private val cache: OfflineCache,
    private val network: NetworkMonitor,
    private val sessionManager: SessionManager,
    private val poolStore: PoolStore,
    private val settingsStore: SettingsStore,
    private val toastBus: ToastBus,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    private val syncMutex = Mutex()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    /** Begins watching connectivity; safe to call more than once. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(network.online, sessionManager.state) { online, session ->
                online && session is SessionState.LoggedIn
            }
                .distinctUntilChanged()
                .collect { ready -> if (ready) syncNow() }
        }
    }

    suspend fun syncNow() {
        if (!syncMutex.tryLock()) return
        _syncing.value = true
        try {
            pushPendingSettings()
            replayPendingReviews()
            refreshCache()
        } finally {
            _syncing.value = false
            syncMutex.unlock()
        }
    }

    private suspend fun pushPendingSettings() {
        val patch = cache.pendingSettingsPatch() ?: return
        when (val result = safeApi { api.patchSettingsRaw(patch) }) {
            is ApiResult.Success -> {
                cache.clearPendingSettings()
                cache.saveSettings(result.data)
                settingsStore.update(result.data)
                toastBus.success("Offline settings changes synced")
            }
            is ApiResult.Error -> {
                // 4xx: the server refused the stored patch — drop it, server
                // state wins. Network errors keep it queued for next time.
                if ((result.code ?: 0) in 400..499) cache.clearPendingSettings()
            }
        }
    }

    private suspend fun replayPendingReviews() {
        val pending = cache.pendingReviews().sortedBy { it.queuedAt }
        if (pending.isEmpty()) return
        var synced = 0
        val done = mutableSetOf<String>()
        for (item in pending) {
            var result = safeApi { api.review(item.cardId, item.request) }
            if (result is ApiResult.Error && result.code == 409) {
                delay(850)
                result = safeApi { api.review(item.cardId, item.request) }
            }
            when (result) {
                is ApiResult.Success -> {
                    done.add(item.id)
                    synced++
                }
                is ApiResult.Error -> {
                    if ((result.code ?: 0) in 400..499) {
                        // Conflict or deleted card: drop the event, server wins.
                        done.add(item.id)
                    } else {
                        // Offline again or a server hiccup: retry on next sync.
                        break
                    }
                }
            }
        }
        if (done.isNotEmpty()) cache.removeReviews(done)
        if (synced > 0) {
            toastBus.success("Synced $synced offline review${if (synced == 1) "" else "s"}")
        }
    }

    /** Pulls everything the app needs to work without a connection. */
    private suspend fun refreshCache() {
        val pools = safeApi { api.pools() }
        if (pools is ApiResult.Success) {
            val active = pools.data.results.filterNot { it.archived }
            cache.savePools(active)
            poolStore.setPools(active)
            for (pool in active) {
                val cards = mutableListOf<FlashcardDto>()
                var page = 1
                while (page <= MAX_PAGES) {
                    val result = safeApi {
                        api.flashcards(pool = pool.id, search = null, page = page, pageSize = 100)
                    }
                    if (result !is ApiResult.Success) return
                    cards.addAll(result.data.results)
                    if (result.data.next == null) break
                    page++
                }
                cache.saveCards(pool.id, cards)
            }
        }
        safeApi { api.overview() }.also { if (it is ApiResult.Success) cache.saveOverview(it.data) }
        safeApi { api.me() }.also { if (it is ApiResult.Success) cache.saveMe(it.data) }
        safeApi { api.models() }.also { if (it is ApiResult.Success) cache.saveModels(it.data.models) }
    }

    private companion object {
        const val MAX_PAGES = 200
    }
}
