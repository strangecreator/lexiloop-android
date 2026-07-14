package ru.lexiloop.app.data.offline

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import ru.lexiloop.app.data.api.AnalyticsResponse
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.api.MeResponse
import ru.lexiloop.app.data.api.ModelOption
import ru.lexiloop.app.data.api.OverviewResponse
import ru.lexiloop.app.data.api.PoolDto
import ru.lexiloop.app.data.api.ReviewRequest
import ru.lexiloop.app.data.api.SettingsDto
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A study result made offline, replayed against /api/study/{id}/review/ later. */
@Serializable
data class PendingReview(
    val id: String,
    @SerialName("card_id") val cardId: Int,
    val request: ReviewRequest,
    @SerialName("queued_at") val queuedAt: Long,
)

/**
 * File-backed JSON cache: the full flashcard set (per pool), pools, settings,
 * overview, plus the queues of work recorded while offline. Images stay
 * online-only by design.
 */
@Singleton
class OfflineCache @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val dir = File(context.filesDir, "offline")
    private val mutex = Mutex()

    // --- Snapshots ---

    suspend fun savePools(pools: List<PoolDto>) = write("pools.json", pools)
    suspend fun loadPools(): List<PoolDto>? = read("pools.json")

    suspend fun saveCards(pool: Int, cards: List<FlashcardDto>) = write("cards-$pool.json", cards)
    suspend fun loadCards(pool: Int): List<FlashcardDto>? = read("cards-$pool.json")

    suspend fun saveMe(me: MeResponse) = write("me.json", me)
    suspend fun loadMe(): MeResponse? = read("me.json")

    suspend fun saveSettings(settings: SettingsDto) = write("settings.json", settings)
    suspend fun loadSettings(): SettingsDto? = read("settings.json")

    suspend fun saveOverview(overview: OverviewResponse) = write("overview.json", overview)
    suspend fun loadOverview(): OverviewResponse? = read("overview.json")

    suspend fun saveAnalytics(analytics: AnalyticsResponse) = write("analytics.json", analytics)
    suspend fun loadAnalytics(): AnalyticsResponse? = read("analytics.json")

    suspend fun saveModels(models: List<ModelOption>) = write("models.json", models)
    suspend fun loadModels(): List<ModelOption>? = read("models.json")

    /** Keeps the card cache fresh after online mutations that return the card. */
    suspend fun upsertCard(card: FlashcardDto) {
        val cards = loadCards(card.pool) ?: return
        val next = if (cards.any { it.id == card.id }) {
            cards.map { if (it.id == card.id) card else it }
        } else {
            cards + card
        }
        saveCards(card.pool, next)
    }

    suspend fun removeCard(cardId: Int) {
        val pools = loadPools() ?: return
        for (pool in pools) {
            val cards = loadCards(pool.id) ?: continue
            if (cards.any { it.id == cardId }) {
                saveCards(pool.id, cards.filterNot { it.id == cardId })
            }
        }
    }

    // --- Pending reviews (offline study progress) ---

    suspend fun pendingReviews(): List<PendingReview> = read("pending-reviews.json") ?: emptyList()

    suspend fun enqueueReview(review: PendingReview) {
        write("pending-reviews.json", pendingReviews() + review)
    }

    suspend fun removeReviews(ids: Set<String>) {
        write("pending-reviews.json", pendingReviews().filterNot { it.id in ids })
    }

    // --- Pending settings (accumulated partial PATCH, field-level last write wins) ---

    suspend fun pendingSettingsPatch(): JsonObject? = read("pending-settings.json")

    suspend fun mergePendingSettings(patch: JsonObject) {
        val merged = JsonObject((pendingSettingsPatch() ?: JsonObject(emptyMap())) + patch)
        write("pending-settings.json", merged)
    }

    suspend fun clearPendingSettings() = delete("pending-settings.json")

    /** Wipes everything, e.g. on sign-out. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    // --- Plumbing: atomic file writes, forgiving reads ---

    private suspend inline fun <reified T> write(name: String, value: T) {
        val text = try {
            json.encodeToString(value)
        } catch (_: Exception) {
            return
        }
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    dir.mkdirs()
                    val tmp = File(dir, "$name.tmp")
                    tmp.writeText(text)
                    tmp.renameTo(File(dir, name))
                } catch (_: Exception) {
                    // A failed cache write must never break the online flow.
                }
            }
        }
    }

    private suspend inline fun <reified T> read(name: String): T? {
        val text = withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = File(dir, name)
                if (file.exists()) {
                    try {
                        file.readText()
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
        } ?: return null
        return try {
            json.decodeFromString<T>(text)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        mutex.withLock { File(dir, name).delete() }
    }
}
