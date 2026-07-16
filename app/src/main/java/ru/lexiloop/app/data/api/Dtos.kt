package ru.lexiloop.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class AuthRequest(
    val username: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val username: String,
)

@Serializable
data class Paged<T>(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList(),
)

@Serializable
data class PoolDto(
    val id: Int,
    val name: String,
    val description: String = "",
    val accent: String = "",
    val archived: Boolean = false,
    @SerialName("card_count") val cardCount: Int = 0,
    @SerialName("due_count") val dueCount: Int = 0,
)

@Serializable
data class PoolWriteBody(
    val name: String? = null,
    val description: String? = null,
    val accent: String? = null,
)

@Serializable
data class PoolTransferBody(
    @SerialName("target_pool") val targetPool: Int,
    val mode: String, // copy | move
)

@Serializable
data class ScheduleDto(
    val state: String = "new",
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("interval_days") val intervalDays: Double = 0.0,
    @SerialName("ease_factor") val easeFactor: Double = 2.5,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    @SerialName("last_reviewed_at") val lastReviewedAt: String? = null,
)

@Serializable
data class FlashcardDto(
    val id: Int,
    val pool: Int,
    @SerialName("pool_name") val poolName: String = "",
    val term: String,
    @SerialName("part_of_speech") val partOfSpeech: String = "",
    val ipa: String = "",
    @SerialName("short_definition") val shortDefinition: String = "",
    val definition: String = "",
    // Items are {"sentence": …, "note": …} objects, but legacy rows may hold
    // bare strings. See [exampleSentences].
    val examples: List<JsonElement> = emptyList(),
    val forms: JsonObject? = null,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val collocations: List<String> = emptyList(),
    @SerialName("usage_notes") val usageNotes: String = "",
    val aliases: List<String> = emptyList(),
    val suspended: Boolean = false,
    @SerialName("has_image") val hasImage: Boolean = false,
    @SerialName("image_key") val imageKey: String = "",
    val schedule: ScheduleDto? = null,
) {
    /** Example sentences with optional notes, tolerant of both payload shapes. */
    fun exampleSentences(): List<CardExample> = examples.mapNotNull { element ->
        when (element) {
            is JsonObject -> {
                val sentence = (element["sentence"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                val note = (element["note"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (sentence.isEmpty()) null else CardExample(sentence, note)
            }
            is JsonPrimitive -> element.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { CardExample(it, "") }
            else -> null
        }
    }

    /** "key: value" word forms, tolerant of non-string values. */
    fun formEntries(): List<Pair<String, String>> = forms
        ?.mapNotNull { (key, value) ->
            val text = (value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (text.isEmpty()) null else key to text
        }
        ?: emptyList()
}

data class CardExample(val sentence: String, val note: String)

@Serializable
data class ExampleBody(val sentence: String, val note: String = "")

@Serializable
data class CardWriteBody(
    val pool: Int,
    val term: String,
    @SerialName("part_of_speech") val partOfSpeech: String = "",
    val ipa: String = "",
    @SerialName("short_definition") val shortDefinition: String = "",
    val definition: String,
    val examples: List<ExampleBody> = emptyList(),
    val forms: Map<String, String> = emptyMap(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val collocations: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    @SerialName("usage_notes") val usageNotes: String = "",
)

@Serializable
data class GenerateBody(val pool: Int, val term: String)

@Serializable
data class ImageLinkBody(val url: String)

@Serializable
data class QueueBreakdownDto(
    val new: Int = 0,
    val learning: Int = 0,
    val review: Int = 0,
)

@Serializable
data class UpcomingImageDto(
    val id: Int,
    @SerialName("image_key") val imageKey: String = "",
)

@Serializable
data class NextCardResponse(
    val card: FlashcardDto? = null,
    val direction: String? = null,
    val prompt: String? = null,
    val mode: String = "due",
    val message: String? = null,
    @SerialName("practice_complete") val practiceComplete: Boolean = false,
    @SerialName("queue_count") val queueCount: Int = 0,
    @SerialName("round_total") val roundTotal: Int = 0,
    @SerialName("round_completed") val roundCompleted: Int = 0,
    @SerialName("queue_breakdown") val queueBreakdown: QueueBreakdownDto? = null,
    @SerialName("show_images") val showImages: Boolean = false,
    @SerialName("upcoming_images") val upcomingImages: List<UpcomingImageDto> = emptyList(),
)

@Serializable
data class JudgeRequest(
    val answer: String,
    val direction: String,
    @SerialName("response_ms") val responseMs: Long = 0,
    val practice: Boolean = false,
    @SerialName("hint_revealed_letters") val hintRevealedLetters: Int = 0,
    @SerialName("hint_total_letters") val hintTotalLetters: Int = 0,
    // This device's review-timing band; the server falls back to the account
    // settings when absent.
    @SerialName("easy_seconds") val easySeconds: Int? = null,
    @SerialName("good_seconds") val goodSeconds: Int? = null,
)

@Serializable
data class JudgeResponse(
    val grading: String = "",
    val score: Int = 0,
    val verdict: String = "",
    val feedback: String = "",
    @SerialName("matched_concepts") val matchedConcepts: List<String> = emptyList(),
    @SerialName("missing_or_wrong_concepts") val missingOrWrongConcepts: List<String> = emptyList(),
    val accepted: Boolean = false,
    // Since v1.13 the judge endpoint saves the review in the same request.
    @SerialName("review_recorded") val reviewRecorded: Boolean = false,
)

@Serializable
data class ReviewRequest(
    val direction: String,
    val answer: String,
    val accepted: Boolean,
    @SerialName("judge_score") val judgeScore: Int? = null,
    @SerialName("judge_verdict") val judgeVerdict: String = "",
    val feedback: String = "",
    @SerialName("response_ms") val responseMs: Long = 0,
    val practice: Boolean = false,
    @SerialName("hint_revealed_letters") val hintRevealedLetters: Int = 0,
    @SerialName("hint_total_letters") val hintTotalLetters: Int = 0,
    // This device's review-timing band; the server falls back to the account
    // settings when absent.
    @SerialName("easy_seconds") val easySeconds: Int? = null,
    @SerialName("good_seconds") val goodSeconds: Int? = null,
)

@Serializable
data class ReviewScheduleDto(
    val state: String = "new",
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("interval_days") val intervalDays: Double = 0.0,
    @SerialName("ease_factor") val easeFactor: Double = 2.5,
    val repetitions: Int = 0,
    val lapses: Int = 0,
)

@Serializable
data class ReviewResponse(
    val schedule: ReviewScheduleDto? = null,
    val practice: Boolean = false,
    @SerialName("automatic_rating") val automaticRating: Int = 0,
    @SerialName("automatic_rating_label") val automaticRatingLabel: String = "",
)

@Serializable
data class ActivityDayDto(
    val day: String,
    val reviews: Int = 0,
)

@Serializable
data class OverviewResponse(
    @SerialName("total_cards") val totalCards: Int = 0,
    @SerialName("due_now") val dueNow: Int = 0,
    @SerialName("new_cards") val newCards: Int = 0,
    @SerialName("reviews_today") val reviewsToday: Int = 0,
    val retention: Double = 0.0,
    val streak: Int = 0,
    val activity: List<ActivityDayDto> = emptyList(),
)

// --- Settings (mirrors the ProfileSerializer field list) ---

@Serializable
data class SettingsDto(
    val theme: String = "system",
    @SerialName("accent_color") val accentColor: String = "emerald",
    @SerialName("study_directions") val studyDirections: List<String> = emptyList(),
    @SerialName("generation_model") val generationModel: String = "",
    @SerialName("has_generation_token") val hasGenerationToken: Boolean = false,
    @SerialName("judge_model") val judgeModel: String = "",
    @SerialName("has_judge_token") val hasJudgeToken: Boolean = false,
    @SerialName("image_model") val imageModel: String = "",
    @SerialName("has_image_token") val hasImageToken: Boolean = false,
    @SerialName("show_card_images") val showCardImages: Boolean = true,
    @SerialName("show_images_term_to_definition") val showImagesTermToDefinition: Boolean = true,
    @SerialName("show_images_definition_to_term") val showImagesDefinitionToTerm: Boolean = true,
    @SerialName("show_images_term_to_sentence") val showImagesTermToSentence: Boolean = true,
    @SerialName("image_animations") val imageAnimations: List<String> = listOf("mist", "ripple", "drift"),
    @SerialName("image_animation_durations") val imageAnimationDurations: Map<String, Double> = emptyMap(),
    @SerialName("image_prefetch_count") val imagePrefetchCount: Int = 2,
    @SerialName("token_status") val tokenStatus: Map<String, Boolean> = emptyMap(),
    @SerialName("judge_acceptance_score") val judgeAcceptanceScore: Int = 5,
    @SerialName("sentence_judge_model") val sentenceJudgeModel: String = "",
    @SerialName("has_sentence_token") val hasSentenceToken: Boolean = false,
    @SerialName("sentence_acceptance_score") val sentenceAcceptanceScore: Int = 5,
    @SerialName("daily_new_limit") val dailyNewLimit: Int = 20,
    @SerialName("learning_steps_minutes") val learningStepsMinutes: List<Double> = listOf(1.0, 10.0),
    @SerialName("relearning_steps_minutes") val relearningStepsMinutes: List<Double> = listOf(10.0),
    @SerialName("graduating_interval_days") val graduatingIntervalDays: Double = 1.0,
    @SerialName("easy_interval_days") val easyIntervalDays: Double = 4.0,
    @SerialName("easy_bonus") val easyBonus: Double = 1.3,
    @SerialName("hard_multiplier") val hardMultiplier: Double = 1.2,
    @SerialName("lapse_multiplier") val lapseMultiplier: Double = 0.5,
    @SerialName("minimum_ease") val minimumEase: Double = 1.3,
    @SerialName("term_to_definition_easy_seconds") val termToDefinitionEasySeconds: Int = 12,
    @SerialName("term_to_definition_good_seconds") val termToDefinitionGoodSeconds: Int = 35,
    @SerialName("definition_to_term_easy_seconds") val definitionToTermEasySeconds: Int = 6,
    @SerialName("definition_to_term_good_seconds") val definitionToTermGoodSeconds: Int = 18,
    @SerialName("term_to_sentence_easy_seconds") val termToSentenceEasySeconds: Int = 20,
    @SerialName("term_to_sentence_good_seconds") val termToSentenceGoodSeconds: Int = 60,
)

/**
 * Writable subset sent by the Settings page; read-only fields stay out.
 * Appearance/image/timing fields that are device-local in the app (accent
 * color, image display, prefetch, animations, review timing) are also absent:
 * the PATCH is partial, so the site's copy of those keeps its own values.
 */
@Serializable
data class SettingsWriteBody(
    val theme: String,
    @SerialName("generation_model") val generationModel: String,
    @SerialName("judge_model") val judgeModel: String,
    @SerialName("image_model") val imageModel: String,
    @SerialName("judge_acceptance_score") val judgeAcceptanceScore: Int,
    @SerialName("sentence_judge_model") val sentenceJudgeModel: String,
    @SerialName("sentence_acceptance_score") val sentenceAcceptanceScore: Int,
    @SerialName("daily_new_limit") val dailyNewLimit: Int,
    @SerialName("learning_steps_minutes") val learningStepsMinutes: List<Double>,
    @SerialName("relearning_steps_minutes") val relearningStepsMinutes: List<Double>,
    @SerialName("graduating_interval_days") val graduatingIntervalDays: Double,
    @SerialName("easy_interval_days") val easyIntervalDays: Double,
    @SerialName("easy_bonus") val easyBonus: Double,
    @SerialName("hard_multiplier") val hardMultiplier: Double,
    @SerialName("lapse_multiplier") val lapseMultiplier: Double,
    @SerialName("minimum_ease") val minimumEase: Double,
    // A non-empty string replaces the provider key, "" removes it, absent keys
    // stay untouched. Null means "no key changes staged".
    @SerialName("provider_tokens") val providerTokens: Map<String, String>? = null,
)

@Serializable
data class ThemePatchBody(val theme: String)

@Serializable
data class MeResponse(
    val username: String,
    val settings: SettingsDto? = null,
)

// --- Model catalog ---

@Serializable
data class ModelOption(
    val id: String,
    val label: String = "",
    val provider: String = "",
    val description: String = "",
    val badge: String = "",
    @SerialName("key_url") val keyUrl: String = "",
    @SerialName("token_label") val tokenLabel: String = "",
    @SerialName("token_provider") val tokenProvider: String = "",
    @SerialName("recommended_for") val recommendedFor: List<String> = emptyList(),
)

@Serializable
data class ModelsResponse(val models: List<ModelOption> = emptyList())

// --- Analytics ---

@Serializable
data class AnalyticsTotals(
    val cost: Double = 0.0,
    val tokens: Long = 0,
    val calls: Int = 0,
    @SerialName("average_latency") val averageLatency: Double = 0.0,
)

@Serializable
data class AnalyticsDaily(
    val day: String = "",
    val cost: Double = 0.0,
    val tokens: Long = 0,
    val calls: Int = 0,
)

@Serializable
data class AnalyticsPoolRow(
    @SerialName("pool_id") val poolId: Int? = null,
    @SerialName("pool__name") val poolName: String = "",
    val accent: String = "",
    val cost: Double = 0.0,
    val tokens: Long = 0,
    val calls: Int = 0,
)

@Serializable
data class AnalyticsFailure(
    val id: Int = 0,
    val operation: String = "",
    val model: String = "",
    val error: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class AnalyticsResponse(
    val daily: List<AnalyticsDaily> = emptyList(),
    @SerialName("by_pool") val byPool: List<AnalyticsPoolRow> = emptyList(),
    val totals: AnalyticsTotals = AnalyticsTotals(),
    val failures: List<AnalyticsFailure> = emptyList(),
)

// --- Bulk generation ---

@Serializable
data class NormalizeBody(val terms: String)

@Serializable
data class NormalizeChange(
    val source: String = "",
    val normalized: String = "",
    val status: String = "",
)

@Serializable
data class NormalizeError(
    val term: String = "",
    val normalized: String? = null,
    val error: String = "",
)

@Serializable
data class NormalizeResponse(
    val normalized: List<String> = emptyList(),
    val changes: List<NormalizeChange> = emptyList(),
    val errors: List<NormalizeError> = emptyList(),
    @SerialName("input_count") val inputCount: Int = 0,
)

@Serializable
data class BulkStartBody(
    val pool: Int,
    val terms: List<String>,
    @SerialName("batch_size") val batchSize: Int,
)

@Serializable
data class BulkFailedTerm(
    val term: String = "",
    val error: String = "",
    val attempts: Int = 0,
)

@Serializable
data class BulkJob(
    val id: String,
    val status: String = "queued",
    @SerialName("created_count") val createdCount: Int = 0,
    @SerialName("skipped_count") val skippedCount: Int = 0,
    @SerialName("failed_count") val failedCount: Int = 0,
    @SerialName("processed_count") val processedCount: Int = 0,
    @SerialName("total_count") val totalCount: Int = 0,
    val progress: Double = 0.0,
    @SerialName("current_round") val currentRound: Int = 0,
    @SerialName("max_rounds") val maxRounds: Int = 0,
    val error: String = "",
    @SerialName("failed_terms") val failedTerms: List<BulkFailedTerm> = emptyList(),
    @SerialName("updated_at") val updatedAt: String = "",
) {
    val isActive: Boolean get() = status == "queued" || status == "running"
}
