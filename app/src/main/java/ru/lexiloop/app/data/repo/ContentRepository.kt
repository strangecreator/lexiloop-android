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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val api: LexiLoopApi,
) {
    suspend fun me(): ApiResult<MeResponse> = safeApi { api.me() }

    suspend fun overview(): ApiResult<OverviewResponse> = safeApi { api.overview() }

    suspend fun analytics(): ApiResult<AnalyticsResponse> = safeApi { api.analytics() }

    suspend fun models(): ApiResult<List<ModelOption>> = safeApi { api.models() }.map { it.models }

    // --- Settings ---

    suspend fun saveSettings(body: SettingsWriteBody): ApiResult<SettingsDto> =
        safeApi { api.saveSettings(body) }

    suspend fun patchTheme(theme: String): ApiResult<SettingsDto> =
        safeApi { api.patchTheme(ThemePatchBody(theme)) }

    // --- Pools ---

    suspend fun pools(): ApiResult<List<PoolDto>> =
        safeApi { api.pools() }.map { page -> page.results.filterNot { it.archived } }

    suspend fun createPool(name: String, description: String): ApiResult<PoolDto> =
        safeApi { api.createPool(PoolWriteBody(name = name, description = description)) }

    suspend fun patchPool(id: Int, body: PoolWriteBody): ApiResult<PoolDto> =
        safeApi { api.patchPool(id, body) }

    suspend fun deletePool(id: Int): ApiResult<Unit> = safeApi { api.deletePool(id) }

    suspend fun transferPool(id: Int, target: Int, mode: String): ApiResult<Unit> =
        safeApi { api.transferPool(id, PoolTransferBody(targetPool = target, mode = mode)) }

    // --- Flashcards ---

    suspend fun flashcards(pool: Int?, search: String?, page: Int): ApiResult<Paged<FlashcardDto>> =
        safeApi {
            api.flashcards(pool = pool, search = search?.takeIf { it.isNotBlank() }, page = page)
        }

    suspend fun createCard(body: CardWriteBody): ApiResult<FlashcardDto> =
        safeApi { api.createCard(body) }

    suspend fun patchCard(id: Int, body: CardWriteBody): ApiResult<FlashcardDto> =
        safeApi { api.patchCard(id, body) }

    suspend fun suspendCard(id: Int): ApiResult<FlashcardDto> = safeApi { api.suspendCard(id) }

    suspend fun unsuspendCard(id: Int): ApiResult<FlashcardDto> = safeApi { api.unsuspendCard(id) }

    suspend fun deleteCard(id: Int): ApiResult<Unit> = safeApi { api.deleteCard(id) }

    // --- Card images ---

    suspend fun setCardImageFromLink(id: Int, url: String): ApiResult<FlashcardDto> =
        safeApi { api.setCardImageFromLink(id, ImageLinkBody(url)) }

    suspend fun uploadCardImage(id: Int, bytes: ByteArray, fileName: String): ApiResult<FlashcardDto> =
        safeApi {
            val body = bytes.toRequestBody("image/*".toMediaType())
            api.uploadCardImage(id, MultipartBody.Part.createFormData("file", fileName, body))
        }

    suspend fun removeCardImage(id: Int): ApiResult<FlashcardDto> = safeApi { api.removeCardImage(id) }

    // --- Generation ---

    suspend fun generate(pool: Int, term: String): ApiResult<FlashcardDto> =
        safeApi { api.generate(GenerateBody(pool = pool, term = term)) }

    suspend fun normalizeTerms(terms: String): ApiResult<NormalizeResponse> =
        safeApi { api.normalizeTerms(NormalizeBody(terms)) }

    suspend fun bulkJobs(pool: Int): ApiResult<List<BulkJob>> = safeApi { api.bulkJobs(pool) }

    suspend fun startBulkJob(pool: Int, terms: List<String>, batchSize: Int): ApiResult<BulkJob> =
        safeApi { api.startBulkJob(BulkStartBody(pool = pool, terms = terms, batchSize = batchSize)) }

    suspend fun bulkJob(id: String): ApiResult<BulkJob> = safeApi { api.bulkJob(id) }

    suspend fun cancelBulkJob(id: String): ApiResult<BulkJob> = safeApi { api.cancelBulkJob(id) }

    // --- Study ---

    suspend fun nextCard(pool: Int?, mode: String, excludeIds: List<Int>): ApiResult<NextCardResponse> =
        safeApi {
            api.nextCard(
                pool = pool,
                mode = mode,
                exclude = excludeIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            )
        }

    suspend fun judge(cardId: Int, request: JudgeRequest): ApiResult<JudgeResponse> =
        safeApi { api.judge(cardId, request) }

    suspend fun review(cardId: Int, request: ReviewRequest): ApiResult<ReviewResponse> =
        safeApi { api.review(cardId, request) }
}
