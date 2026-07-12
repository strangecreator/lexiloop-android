package ru.lexiloop.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
    // Kept loose on purpose: items are {"sentence": …, "note": …} objects, but
    // legacy rows may hold bare strings. See [exampleSentences].
    val examples: List<JsonElement> = emptyList(),
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
            is JsonPrimitive -> element.jsonPrimitive.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { CardExample(it, "") }
            else -> null
        }
    }
}

data class CardExample(val sentence: String, val note: String)

@Serializable
data class QueueBreakdownDto(
    val new: Int = 0,
    val learning: Int = 0,
    val review: Int = 0,
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
)

@Serializable
data class JudgeRequest(
    val answer: String,
    val direction: String,
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

@Serializable
data class ProfileDto(
    val theme: String = "system",
    @SerialName("accent_color") val accentColor: String = "",
    @SerialName("study_directions") val studyDirections: List<String> = emptyList(),
    @SerialName("generation_model") val generationModel: String = "",
    @SerialName("judge_model") val judgeModel: String = "",
    @SerialName("daily_new_limit") val dailyNewLimit: Int = 0,
)

@Serializable
data class MeResponse(
    val username: String,
    val settings: ProfileDto? = null,
)
