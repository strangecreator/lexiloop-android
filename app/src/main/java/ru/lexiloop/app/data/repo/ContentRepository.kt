package ru.lexiloop.app.data.repo

import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.api.JudgeRequest
import ru.lexiloop.app.data.api.JudgeResponse
import ru.lexiloop.app.data.api.LexiLoopApi
import ru.lexiloop.app.data.api.MeResponse
import ru.lexiloop.app.data.api.NextCardResponse
import ru.lexiloop.app.data.api.OverviewResponse
import ru.lexiloop.app.data.api.Paged
import ru.lexiloop.app.data.api.PoolDto
import ru.lexiloop.app.data.api.ReviewRequest
import ru.lexiloop.app.data.api.ReviewResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val api: LexiLoopApi,
) {
    suspend fun overview(): ApiResult<OverviewResponse> = safeApi { api.overview() }

    suspend fun pools(): ApiResult<List<PoolDto>> =
        safeApi { api.pools() }.map { page -> page.results.filterNot { it.archived } }

    suspend fun flashcards(
        pool: Int?,
        search: String?,
        page: Int,
    ): ApiResult<Paged<FlashcardDto>> = safeApi {
        api.flashcards(pool = pool, search = search?.takeIf { it.isNotBlank() }, page = page)
    }

    suspend fun suspendCard(id: Int): ApiResult<FlashcardDto> = safeApi { api.suspendCard(id) }

    suspend fun unsuspendCard(id: Int): ApiResult<FlashcardDto> = safeApi { api.unsuspendCard(id) }

    suspend fun deleteCard(id: Int): ApiResult<Unit> = safeApi { api.deleteCard(id) }

    suspend fun nextCard(pool: Int?, mode: String, excludeIds: List<Int>): ApiResult<NextCardResponse> =
        safeApi {
            api.nextCard(
                pool = pool,
                mode = mode,
                exclude = excludeIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            )
        }

    suspend fun judge(cardId: Int, answer: String, direction: String): ApiResult<JudgeResponse> =
        safeApi { api.judge(cardId, JudgeRequest(answer = answer, direction = direction)) }

    suspend fun review(cardId: Int, request: ReviewRequest): ApiResult<ReviewResponse> =
        safeApi { api.review(cardId, request) }

    suspend fun me(): ApiResult<MeResponse> = safeApi { api.me() }
}
