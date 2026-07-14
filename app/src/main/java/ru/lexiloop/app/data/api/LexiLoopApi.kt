package ru.lexiloop.app.data.api

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
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

    // --- Overview / analytics / models ---

    @GET("api/overview/")
    suspend fun overview(): OverviewResponse

    @GET("api/analytics/")
    suspend fun analytics(): AnalyticsResponse

    @GET("api/models/")
    suspend fun models(): ModelsResponse

    // --- Settings ---

    @GET("api/settings/")
    suspend fun settings(): SettingsDto

    @PATCH("api/settings/")
    suspend fun saveSettings(@Body body: SettingsWriteBody): SettingsDto

    @PATCH("api/settings/")
    suspend fun patchTheme(@Body body: ThemePatchBody): SettingsDto

    /** Partial settings patch replayed from the offline queue. */
    @PATCH("api/settings/")
    suspend fun patchSettingsRaw(@Body body: kotlinx.serialization.json.JsonObject): SettingsDto

    // --- Pools ---

    @GET("api/pools/")
    suspend fun pools(@Query("page_size") pageSize: Int = 100): Paged<PoolDto>

    @POST("api/pools/")
    suspend fun createPool(@Body body: PoolWriteBody): PoolDto

    @PATCH("api/pools/{id}/")
    suspend fun patchPool(@Path("id") id: Int, @Body body: PoolWriteBody): PoolDto

    @DELETE("api/pools/{id}/")
    suspend fun deletePool(@Path("id") id: Int)

    @POST("api/pools/{id}/transfer/")
    suspend fun transferPool(@Path("id") id: Int, @Body body: PoolTransferBody)

    // --- Flashcards ---

    @GET("api/flashcards/")
    suspend fun flashcards(
        @Query("pool") pool: Int? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30,
    ): Paged<FlashcardDto>

    @POST("api/flashcards/")
    suspend fun createCard(@Body body: CardWriteBody): FlashcardDto

    @PATCH("api/flashcards/{id}/")
    suspend fun patchCard(@Path("id") id: Int, @Body body: CardWriteBody): FlashcardDto

    @POST("api/flashcards/{id}/suspend/")
    suspend fun suspendCard(@Path("id") id: Int): FlashcardDto

    @POST("api/flashcards/{id}/unsuspend/")
    suspend fun unsuspendCard(@Path("id") id: Int): FlashcardDto

    @DELETE("api/flashcards/{id}/")
    suspend fun deleteCard(@Path("id") id: Int)

    // --- Card images ---

    @POST("api/flashcards/{id}/image/")
    suspend fun setCardImageFromLink(@Path("id") id: Int, @Body body: ImageLinkBody): FlashcardDto

    @Multipart
    @POST("api/flashcards/{id}/image/")
    suspend fun uploadCardImage(@Path("id") id: Int, @Part file: MultipartBody.Part): FlashcardDto

    @DELETE("api/flashcards/{id}/image/")
    suspend fun removeCardImage(@Path("id") id: Int): FlashcardDto

    // --- Generation ---

    @POST("api/generate/")
    suspend fun generate(@Body body: GenerateBody): FlashcardDto

    @POST("api/generate/normalize/")
    suspend fun normalizeTerms(@Body body: NormalizeBody): NormalizeResponse

    @GET("api/generate/bulk/")
    suspend fun bulkJobs(@Query("pool") pool: Int): List<BulkJob>

    @POST("api/generate/bulk/")
    suspend fun startBulkJob(@Body body: BulkStartBody): BulkJob

    @GET("api/generate/bulk/jobs/{id}/")
    suspend fun bulkJob(@Path("id") id: String): BulkJob

    @POST("api/generate/bulk/jobs/{id}/cancel/")
    suspend fun cancelBulkJob(@Path("id") id: String): BulkJob

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
}
