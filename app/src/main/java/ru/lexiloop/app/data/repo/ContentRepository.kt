package ru.lexiloop.app.data.repo

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.lexiloop.app.data.api.BulkJob
import ru.lexiloop.app.data.api.BulkStartBody
import ru.lexiloop.app.data.api.CardWriteBody
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.api.GenerateBody
import ru.lexiloop.app.data.api.ImageLinkBody
import ru.lexiloop.app.data.api.JudgeRequest
import ru.lexiloop.app.data.api.JudgeResponse
import ru.lexiloop.app.data.api.LexiLoopApi
import ru.lexiloop.app.data.api.MeResponse
import ru.lexiloop.app.data.api.ModelOption
import ru.lexiloop.app.data.api.NextCardResponse
import ru.lexiloop.app.data.api.NormalizeBody
import ru.lexiloop.app.data.api.NormalizeResponse
import ru.lexiloop.app.data.api.OverviewResponse
import ru.lexiloop.app.data.api.Paged
import ru.lexiloop.app.data.api.AnalyticsResponse
import ru.lexiloop.app.data.api.PoolDto
import ru.lexiloop.app.data.api.PoolTransferBody
import ru.lexiloop.app.data.api.PoolWriteBody
import ru.lexiloop.app.data.api.ReviewRequest
import ru.lexiloop.app.data.api.ReviewResponse
import ru.lexiloop.app.data.api.SettingsDto
import ru.lexiloop.app.data.api.SettingsWriteBody
import ru.lexiloop.app.data.api.ThemePatchBody
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val api: LexiLoopApi,
    private val cache: ru.lexiloop.app.data.offline.OfflineCache,
    private val json: kotlinx.serialization.json.Json,
) {
    suspend fun me(): ApiResult<MeResponse> =
        withCache({ api.me() }, { cache.saveMe(it) }, { cache.loadMe() })

    suspend fun overview(): ApiResult<OverviewResponse> =
        withCache({ api.overview() }, { cache.saveOverview(it) }, { cache.loadOverview() })

    suspend fun analytics(): ApiResult<AnalyticsResponse> =
        withCache({ api.analytics() }, { cache.saveAnalytics(it) }, { cache.loadAnalytics() })

    suspend fun models(): ApiResult<List<ModelOption>> =
        withCache({ api.models().models }, { cache.saveModels(it) }, { cache.loadModels() })

    // --- Settings ---

    suspend fun saveSettings(body: SettingsWriteBody): ApiResult<SettingsDto> {
        val result = safeApi { api.saveSettings(body) }
        return when {
            result is ApiResult.Success -> {
                cache.clearPendingSettings()
                cache.saveSettings(result.data)
                result
            }
            result is ApiResult.Error && result.code == null -> {
                if (body.providerTokens != null) {
                    ApiResult.Error("You're offline — API keys can only be saved with a connection.")
                } else {
                    queueSettingsPatch(json.encodeToJsonElement(SettingsWriteBody.serializer(), body).jsonObject)
                }
            }
            else -> result
        }
    }

    suspend fun patchTheme(theme: String): ApiResult<SettingsDto> {
        val result = safeApi { api.patchTheme(ThemePatchBody(theme)) }
        return when {
            result is ApiResult.Success -> {
                cache.saveSettings(result.data)
                result
            }
            result is ApiResult.Error && result.code == null ->
                queueSettingsPatch(
                    kotlinx.serialization.json.buildJsonObject {
                        put("theme", kotlinx.serialization.json.JsonPrimitive(theme))
                    },
                )
            else -> result
        }
    }

    /** Offline settings edit: queue the partial PATCH and apply it locally. */
    private suspend fun queueSettingsPatch(patch: kotlinx.serialization.json.JsonObject): ApiResult<SettingsDto> {
        val safePatch = kotlinx.serialization.json.JsonObject(patch - "provider_tokens")
        cache.mergePendingSettings(safePatch)
        val base = cache.loadSettings() ?: SettingsDto()
        val baseJson = json.encodeToJsonElement(SettingsDto.serializer(), base).jsonObject
        val merged = try {
            json.decodeFromJsonElement(
                SettingsDto.serializer(),
                kotlinx.serialization.json.JsonObject(baseJson + safePatch),
            )
        } catch (_: Exception) {
            base
        }
        cache.saveSettings(merged)
        return ApiResult.Success(merged)
    }

    // --- Pools ---

    suspend fun pools(): ApiResult<List<PoolDto>> =
        withCache(
            { api.pools().results.filterNot { it.archived } },
            { cache.savePools(it) },
            { cache.loadPools() },
        )

    suspend fun createPool(name: String, description: String): ApiResult<PoolDto> =
        safeApi { api.createPool(PoolWriteBody(name = name, description = description)) }

    suspend fun patchPool(id: Int, body: PoolWriteBody): ApiResult<PoolDto> =
        safeApi { api.patchPool(id, body) }

    suspend fun deletePool(id: Int): ApiResult<Unit> = safeApi { api.deletePool(id) }

    suspend fun transferPool(id: Int, target: Int, mode: String): ApiResult<Unit> =
        safeApi { api.transferPool(id, PoolTransferBody(targetPool = target, mode = mode)) }

    // --- Flashcards ---

    suspend fun flashcards(pool: Int?, search: String?, page: Int): ApiResult<Paged<FlashcardDto>> {
        val result = safeApi {
            api.flashcards(pool = pool, search = search?.takeIf { it.isNotBlank() }, page = page)
        }
        if (result is ApiResult.Error && result.code == null && pool != null) {
            val cards = cache.loadCards(pool) ?: return result
            val query = search?.trim()?.lowercase().orEmpty()
            val filtered = if (query.isEmpty()) {
                cards
            } else {
                cards.filter { card ->
                    card.term.lowercase().contains(query) ||
                        card.shortDefinition.lowercase().contains(query) ||
                        card.definition.lowercase().contains(query)
                }
            }
            val pageSize = 30
            val slice = filtered.drop((page - 1) * pageSize).take(pageSize)
            return ApiResult.Success(Paged(count = filtered.size, results = slice))
        }
        return result
    }

    suspend fun createCard(body: CardWriteBody): ApiResult<FlashcardDto> =
        safeApi { api.createCard(body) }.alsoCache()

    suspend fun patchCard(id: Int, body: CardWriteBody): ApiResult<FlashcardDto> =
        safeApi { api.patchCard(id, body) }.alsoCache()

    suspend fun suspendCard(id: Int): ApiResult<FlashcardDto> = safeApi { api.suspendCard(id) }.alsoCache()

    suspend fun unsuspendCard(id: Int): ApiResult<FlashcardDto> = safeApi { api.unsuspendCard(id) }.alsoCache()

    suspend fun deleteCard(id: Int): ApiResult<Unit> = safeApi { api.deleteCard(id) }.also {
        if (it is ApiResult.Success) cache.removeCard(id)
    }

    // --- Card images ---

    suspend fun setCardImageFromLink(id: Int, url: String): ApiResult<FlashcardDto> =
        safeApi { api.setCardImageFromLink(id, ImageLinkBody(url)) }.alsoCache()

    suspend fun uploadCardImage(id: Int, bytes: ByteArray, fileName: String): ApiResult<FlashcardDto> =
        safeApi {
            val body = bytes.toRequestBody("image/*".toMediaType())
            api.uploadCardImage(id, MultipartBody.Part.createFormData("file", fileName, body))
        }.alsoCache()

    suspend fun removeCardImage(id: Int): ApiResult<FlashcardDto> = safeApi { api.removeCardImage(id) }.alsoCache()

    // --- Generation ---

    suspend fun generate(pool: Int, term: String): ApiResult<FlashcardDto> =
        safeApi { api.generate(GenerateBody(pool = pool, term = term)) }.alsoCache()

    suspend fun normalizeTerms(terms: String): ApiResult<NormalizeResponse> =
        safeApi { api.normalizeTerms(NormalizeBody(terms)) }

    suspend fun bulkJobs(pool: Int): ApiResult<List<BulkJob>> = safeApi { api.bulkJobs(pool) }

    suspend fun startBulkJob(pool: Int, terms: List<String>, batchSize: Int): ApiResult<BulkJob> =
        safeApi { api.startBulkJob(BulkStartBody(pool = pool, terms = terms, batchSize = batchSize)) }

    suspend fun bulkJob(id: String): ApiResult<BulkJob> = safeApi { api.bulkJob(id) }

    suspend fun cancelBulkJob(id: String): ApiResult<BulkJob> = safeApi { api.cancelBulkJob(id) }

    // --- Study ---

    suspend fun nextCard(
        pool: Int?,
        mode: String,
        excludeIds: List<Int>,
        prefetch: Int? = null,
        directions: List<String>? = null,
    ): ApiResult<NextCardResponse> =
        safeApi {
            api.nextCard(
                pool = pool,
                mode = mode,
                exclude = excludeIds.takeIf { it.isNotEmpty() }?.joinToString(","),
                prefetch = prefetch,
                directions = directions?.takeIf { it.isNotEmpty() }?.joinToString(","),
            )
        }

    suspend fun judge(cardId: Int, request: JudgeRequest): ApiResult<JudgeResponse> =
        safeApi { api.judge(cardId, request) }

    suspend fun review(cardId: Int, request: ReviewRequest): ApiResult<ReviewResponse> =
        safeApi { api.review(cardId, request) }

    // --- Offline plumbing ---

    /**
     * Serve the cached copy when the network (code == null) is the only thing
     * that failed; real API errors pass through untouched.
     */
    private suspend fun <T : Any> withCache(
        fetch: suspend () -> T,
        save: suspend (T) -> Unit,
        load: suspend () -> T?,
    ): ApiResult<T> {
        val result = safeApi { fetch() }
        return when {
            result is ApiResult.Success -> {
                save(result.data)
                result
            }
            result is ApiResult.Error && result.code == null ->
                load()?.let { ApiResult.Success(it) } ?: result
            else -> result
        }
    }

    private suspend fun ApiResult<FlashcardDto>.alsoCache(): ApiResult<FlashcardDto> {
        if (this is ApiResult.Success) cache.upsertCard(data)
        return this
    }
}
