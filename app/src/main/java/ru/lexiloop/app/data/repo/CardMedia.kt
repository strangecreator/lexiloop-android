package ru.lexiloop.app.data.repo

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.lexiloop.app.BuildConfig
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.auth.SessionManager
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Builds authenticated media URLs for card images. */
object CardImages {
    private val base = BuildConfig.API_BASE_URL.trimEnd('/')

    fun imageUrl(card: FlashcardDto, thumb: Boolean = false): String? {
        if (!card.hasImage) return null
        return imageUrl(card.id, card.imageKey, thumb)
    }

    fun imageUrl(cardId: Int, imageKey: String, thumb: Boolean = false): String {
        val size = if (thumb) "&size=thumb" else ""
        // image_key busts Coil's cache whenever a new file is stored server-side.
        val key = URLEncoder.encode(imageKey, "UTF-8")
        return "$base/api/flashcards/$cardId/image/?key=$key$size"
    }
}

/**
 * Streams term pronunciation from the backend TTS endpoint. One player at a
 * time; a new request stops the previous one.
 */
@Singleton
class PronunciationPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
) {
    private var player: MediaPlayer? = null

    fun play(text: String, onError: () -> Unit = {}) {
        stop()
        val token = sessionManager.currentToken ?: return
        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/api/pronunciation/?text=$encoded"
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        try {
            mediaPlayer.setDataSource(
                context,
                Uri.parse(url),
                mapOf("Authorization" to "Token $token"),
            )
            mediaPlayer.setOnPreparedListener { it.start() }
            mediaPlayer.setOnCompletionListener { stop() }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                stop()
                onError()
                true
            }
            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            stop()
            onError()
        }
    }

    fun stop() {
        player?.let { current ->
            try {
                current.release()
            } catch (_: Exception) {
                // Releasing an already-broken player must never crash playback UI.
            }
        }
        player = null
    }
}
