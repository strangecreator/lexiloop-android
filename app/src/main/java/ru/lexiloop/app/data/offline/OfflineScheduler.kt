package ru.lexiloop.app.data.offline

import ru.lexiloop.app.data.api.ScheduleDto
import ru.lexiloop.app.data.api.SettingsDto
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A faithful port of the server's Anki/SM-2 scheduler
 * (backend/learning/services/scheduler.py), applied to cached cards while the
 * device is offline. The server remains authoritative: queued reviews are
 * replayed on reconnect and the card cache is re-downloaded, so any local
 * result is only a bridge between syncs.
 */
object OfflineScheduler {

    const val AGAIN = 1
    const val HARD = 2
    const val GOOD = 3
    const val EASY = 4

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val MINUTE_MS = 60L * 1000

    fun parseInstantMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(value).toEpochMilli() }
            .getOrNull()
    }

    private fun steps(raw: List<Double>, fallback: List<Int>): List<Int> {
        // Mirrors _steps: int() truncation, then a floor of one minute.
        val parsed = raw.filter { it.isFinite() }.map { max(1, it.toInt()) }
        return parsed.ifEmpty { fallback }
    }

    /** scheduler.automatic_rating: correctness plus recall speed pick the rating. */
    fun automaticRating(
        accepted: Boolean,
        responseMs: Long,
        easySeconds: Int,
        goodSeconds: Int,
        isDefinitionToTerm: Boolean,
        hintRevealedLetters: Int,
        hintTotalLetters: Int,
    ): Int {
        if (!accepted) return AGAIN
        val milliseconds = responseMs.coerceIn(0, 60L * 60 * 1000)
        var base = if (milliseconds <= 0L) {
            GOOD
        } else {
            val easy = max(1, easySeconds)
            val good = max(easy + 1, goodSeconds)
            val seconds = milliseconds / 1000.0
            when {
                seconds < easy -> EASY
                seconds < good -> GOOD
                else -> HARD
            }
        }
        if (isDefinitionToTerm && hintTotalLetters > 0) {
            val revealed = hintRevealedLetters.coerceIn(0, hintTotalLetters)
            val ratio = revealed.toDouble() / max(1, hintTotalLetters)
            base = when {
                ratio > 0.40 -> AGAIN
                ratio > 0.20 -> min(base, HARD)
                revealed > 0 -> min(base, GOOD)
                else -> base
            }
        }
        return base
    }

    /** scheduler.apply_rating: the state machine that produces the next due date. */
    fun applyRating(schedule: ScheduleDto, rating: Int, settings: SettingsDto, nowMillis: Long): ScheduleDto {
        val learning = steps(settings.learningStepsMinutes, listOf(1, 10))
        val relearning = steps(settings.relearningStepsMinutes, listOf(10))
        val bounded = rating.coerceIn(AGAIN, EASY)
        val now = Instant.ofEpochMilli(nowMillis).toString()

        fun atStep(state: String, stepsList: List<Int>, index: Int): ScheduleDto {
            val step = min(index, stepsList.size - 1)
            return schedule.copy(
                state = state,
                stepIndex = step,
                dueAt = Instant.ofEpochMilli(nowMillis + stepsList[step] * MINUTE_MS).toString(),
                intervalDays = 0.0,
            )
        }

        fun inDays(state: String, intervalDays: Double, easeFactor: Double = schedule.easeFactor, lapses: Int = schedule.lapses): ScheduleDto =
            schedule.copy(
                state = state,
                intervalDays = intervalDays,
                easeFactor = easeFactor,
                lapses = lapses,
                stepIndex = 0,
                dueAt = Instant.ofEpochMilli(nowMillis + (intervalDays * DAY_MS).roundToLong()).toString(),
            )

        val next = when (schedule.state) {
            "new" -> when {
                bounded == AGAIN -> atStep("learning", learning, 0)
                bounded == HARD -> atStep("learning", learning, 0).copy(
                    dueAt = Instant.ofEpochMilli(nowMillis + max(1, (learning[0] * 1.5).roundToInt()) * MINUTE_MS).toString(),
                )
                bounded == GOOD && learning.size > 1 -> atStep("learning", learning, 1)
                else -> inDays(
                    "review",
                    if (bounded == EASY) settings.easyIntervalDays else settings.graduatingIntervalDays,
                )
            }

            "learning", "relearning" -> {
                val stepsList = if (schedule.state == "relearning") relearning else learning
                when {
                    bounded == AGAIN -> atStep(schedule.state, stepsList, 0)
                    bounded == HARD -> {
                        val current = stepsList[min(schedule.stepIndex, stepsList.size - 1)]
                        schedule.copy(
                            dueAt = Instant.ofEpochMilli(nowMillis + max(1, (current * 1.5).roundToInt()) * MINUTE_MS).toString(),
                        )
                    }
                    bounded == EASY || schedule.stepIndex + 1 >= stepsList.size -> {
                        val base = max(settings.graduatingIntervalDays, schedule.intervalDays)
                        inDays("review", base * (if (bounded == EASY) settings.easyBonus else 1.0))
                    }
                    else -> {
                        val step = schedule.stepIndex + 1
                        schedule.copy(
                            stepIndex = step,
                            dueAt = Instant.ofEpochMilli(nowMillis + stepsList[step] * MINUTE_MS).toString(),
                        )
                    }
                }
            }

            else -> { // review
                val old = max(schedule.intervalDays, settings.graduatingIntervalDays)
                when (bounded) {
                    // The server computes a lapse interval but _set_step then
                    // zeroes interval_days; atStep mirrors that end state.
                    AGAIN -> atStep("relearning", relearning, 0).copy(
                        easeFactor = max(settings.minimumEase, schedule.easeFactor - 0.2),
                        lapses = schedule.lapses + 1,
                    )
                    HARD -> inDays(
                        "review",
                        max(settings.graduatingIntervalDays, old * settings.hardMultiplier),
                        easeFactor = max(settings.minimumEase, schedule.easeFactor - 0.15),
                    )
                    GOOD -> inDays("review", max(settings.graduatingIntervalDays, old * schedule.easeFactor))
                    else -> inDays(
                        "review",
                        max(settings.easyIntervalDays, old * schedule.easeFactor * settings.easyBonus),
                        easeFactor = schedule.easeFactor + 0.15,
                    )
                }
            }
        }
        return next.copy(
            repetitions = schedule.repetitions + 1,
            lastReviewedAt = now,
        )
    }

    /** scheduler.priority: what the Study queue serves first. */
    fun priority(schedule: ScheduleDto?, nowMillis: Long): Double {
        val state = schedule?.state ?: "new"
        val dueMillis = parseInstantMillis(schedule?.dueAt) ?: nowMillis
        val overdueHours = max(0.0, (nowMillis - dueMillis) / 3_600_000.0)
        val bonus = when (state) {
            "relearning" -> 300
            "learning" -> 250
            "review" -> 150
            "new" -> 50
            else -> 0
        }
        return bonus + overdueHours + 12.0 * (schedule?.lapses ?: 0) - 0.01 * (schedule?.intervalDays ?: 0.0)
    }

    /** NextCardView's state rank, applied before [priority]. */
    fun stateRank(state: String?): Int = when (state) {
        "relearning" -> 4
        "learning" -> 3
        "review" -> 2
        "new", null -> 1
        else -> 0
    }
}
