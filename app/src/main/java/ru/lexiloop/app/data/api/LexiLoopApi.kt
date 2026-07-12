package ru.lexiloop.app.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LexiLoopApi {

    // --- Auth ---

    @POST("api/auth/login/")
    suspend fun login(@Body body: AuthRequest): AuthResponse

    @POST("api/auth/register/")
    suspend fun register(@Body body: AuthRequest): AuthResponse

    @POST("api/auth/logout/")
    suspend fun logout()

    @GET("api/auth/me/")
    suspend fun me(): MeResponse

    // --- Overview ---

    @GET("api/overview/")
    suspend fun overview(): OverviewResponse

    // --- Pools ---

    @GET("api/pools/")
    suspend fun pools(@Query("page_size") pageSize: Int = 100): Paged<PoolDto>

    // --- Flashcards ---

    @GET("api/flashcards/")
    suspend fun flashcards(
        @Query("pool") pool: Int? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30,
    ): Paged<FlashcardDto>

    @POST("api/flashcards/{id}/suspend/")
    suspend fun suspendCard(@Path("id") id: Int): FlashcardDto

    @POST("api/flashcards/{id}/unsuspend/")
    suspend fun unsuspendCard(@Path("id") id: Int): FlashcardDto

    @DELETE("api/flashcards/{id}/")
    suspend fun deleteCard(@Path("id") id: Int)

    // --- Study ---

    @GET("api/study/next/")
    suspend fun nextCard(
        @Query("pool") pool: Int? = null,
        @Query("mode") mode: String = "due",
        @Query("exclude") exclude: String? = null,
    ): NextCardResponse

    @POST("api/study/{id}/judge/")
    suspend fun judge(@Path("id") cardId: Int, @Body body: JudgeRequest): JudgeResponse

    @POST("api/study/{id}/review/")
    suspend fun review(@Path("id") cardId: Int, @Body body: ReviewRequest): ReviewResponse

    // --- Settings ---

    @GET("api/settings/")
    suspend fun settings(): ProfileDto

    @PATCH("api/settings/")
    suspend fun patchSettings(@Body body: Map<String, String>): ProfileDto
}
