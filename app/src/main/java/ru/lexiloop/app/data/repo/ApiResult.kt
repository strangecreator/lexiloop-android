package ru.lexiloop.app.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Error -> this
}

suspend fun <T> safeApi(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: HttpException) {
    ApiResult.Error(messageFrom(e), e.code())
} catch (e: IOException) {
    ApiResult.Error("Cannot reach the server. Check your connection and try again.")
} catch (e: CancellationException) {
    // Never swallow coroutine cancellation.
    throw e
} catch (e: Exception) {
    ApiResult.Error("Something unexpected went wrong. Please try again.")
}

fun messageFrom(e: HttpException): String {
    val body = try {
        e.response()?.errorBody()?.string()
    } catch (_: IOException) {
        null
    }
    val parsed = body?.let(::parseDrfError)
    if (!parsed.isNullOrBlank()) return parsed
    return when (e.code()) {
        401 -> "Your session has expired. Please sign in again."
        403 -> "You do not have access to this resource."
        404 -> "Not found."
        409 -> "This item is busy right now. Try again in a moment."
        in 500..599 -> "The server had a problem (${e.code()}). Try again later."
        else -> "Request failed (${e.code()})."
    }
}

/**
 * Extracts a human-readable message from a DRF error payload — either
 * {"detail": "…"} or {"field": ["message", …]}.
 */
fun parseDrfError(body: String): String? {
    val element = try {
        Json.parseToJsonElement(body)
    } catch (_: Exception) {
        return null
    }
    val obj = element as? JsonObject ?: return null
    (obj["detail"] as? JsonPrimitive)?.let { return it.content }
    for ((field, value) in obj) {
        val text = when (value) {
            is JsonPrimitive -> value.content
            is JsonArray -> (value.firstOrNull() as? JsonPrimitive)?.content
            else -> null
        }
        if (!text.isNullOrBlank()) {
            return if (field == "non_field_errors") text else "$field: $text"
        }
    }
    return null
}
