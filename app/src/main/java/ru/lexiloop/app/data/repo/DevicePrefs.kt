package ru.lexiloop.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.SettingsDto
import ru.lexiloop.app.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-device study/appearance preferences. Each value is an override: null
 * means "follow the account settings from the site", so a device behaves
 * exactly like the site until the user customizes it in the app. The server
 * copy is never written for these fields.
 */
data class DeviceOverrides(
    val accentColor: String? = null,
    val showCardImages: Boolean? = null,
    val showImagesTermToDefinition: Boolean? = null,
    val showImagesDefinitionToTerm: Boolean? = null,
    val showImagesTermToSentence: Boolean? = null,
    val imagePrefetchCount: Int? = null,
    val imageAnimations: List<String>? = null,
    val imageAnimationDurations: Map<String, Double> = emptyMap(),
    val termToDefinitionEasySeconds: Int? = null,
    val termToDefinitionGoodSeconds: Int? = null,
    val definitionToTermEasySeconds: Int? = null,
    val definitionToTermGoodSeconds: Int? = null,
    val termToSentenceEasySeconds: Int? = null,
    val termToSentenceGoodSeconds: Int? = null,
) {
    /** The device's Easy/Good band for a direction, or null to let the server decide. */
    fun timingFor(direction: String): Pair<Int, Int>? = when (direction) {
        "definition_to_term" ->
            pairOrNull(definitionToTermEasySeconds, definitionToTermGoodSeconds)
        "term_to_sentence" ->
            pairOrNull(termToSentenceEasySeconds, termToSentenceGoodSeconds)
        else -> pairOrNull(termToDefinitionEasySeconds, termToDefinitionGoodSeconds)
    }

    private fun pairOrNull(easy: Int?, good: Int?): Pair<Int, Int>? =
        if (easy != null && good != null) easy to good else null
}

/** Account settings with this device's overrides applied. */
data class EffectiveStudyPrefs(
    val accentColor: String,
    val showCardImages: Boolean,
    val showImagesTermToDefinition: Boolean,
    val showImagesDefinitionToTerm: Boolean,
    val showImagesTermToSentence: Boolean,
    val imagePrefetchCount: Int,
    val imageAnimations: List<String>,
    val imageAnimationDurations: Map<String, Double>,
    val termToDefinitionEasySeconds: Int,
    val termToDefinitionGoodSeconds: Int,
    val definitionToTermEasySeconds: Int,
    val definitionToTermGoodSeconds: Int,
    val termToSentenceEasySeconds: Int,
    val termToSentenceGoodSeconds: Int,
) {
    fun imagesEnabledFor(direction: String): Boolean = showCardImages && when (direction) {
        "definition_to_term" -> showImagesDefinitionToTerm
        "term_to_sentence" -> showImagesTermToSentence
        else -> showImagesTermToDefinition
    }
}

fun resolveStudyPrefs(server: SettingsDto, device: DeviceOverrides): EffectiveStudyPrefs =
    EffectiveStudyPrefs(
        accentColor = device.accentColor ?: server.accentColor,
        showCardImages = device.showCardImages ?: server.showCardImages,
        showImagesTermToDefinition = device.showImagesTermToDefinition
            ?: server.showImagesTermToDefinition,
        showImagesDefinitionToTerm = device.showImagesDefinitionToTerm
            ?: server.showImagesDefinitionToTerm,
        showImagesTermToSentence = device.showImagesTermToSentence
            ?: server.showImagesTermToSentence,
        imagePrefetchCount = device.imagePrefetchCount ?: server.imagePrefetchCount,
        imageAnimations = device.imageAnimations ?: server.imageAnimations,
        imageAnimationDurations = server.imageAnimationDurations + device.imageAnimationDurations,
        termToDefinitionEasySeconds = device.termToDefinitionEasySeconds
            ?: server.termToDefinitionEasySeconds,
        termToDefinitionGoodSeconds = device.termToDefinitionGoodSeconds
            ?: server.termToDefinitionGoodSeconds,
        definitionToTermEasySeconds = device.definitionToTermEasySeconds
            ?: server.definitionToTermEasySeconds,
        definitionToTermGoodSeconds = device.definitionToTermGoodSeconds
            ?: server.definitionToTermGoodSeconds,
        termToSentenceEasySeconds = device.termToSentenceEasySeconds
            ?: server.termToSentenceEasySeconds,
        termToSentenceGoodSeconds = device.termToSentenceGoodSeconds
            ?: server.termToSentenceGoodSeconds,
    )

/**
 * Preferences that belong to this device rather than the account — the text
 * scale plus the [DeviceOverrides]. Server settings stay untouched.
 */
@Singleton
class DevicePrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val fontScaleKey = floatPreferencesKey("font_scale")
    private val accentKey = stringPreferencesKey("device_accent_color")
    private val showImagesKey = booleanPreferencesKey("device_show_card_images")
    private val showImagesT2dKey = booleanPreferencesKey("device_show_images_t2d")
    private val showImagesD2tKey = booleanPreferencesKey("device_show_images_d2t")
    private val showImagesT2sKey = booleanPreferencesKey("device_show_images_t2s")
    private val prefetchKey = intPreferencesKey("device_image_prefetch")
    // Ordered comma list; present-but-empty means "plain fade" was chosen.
    private val animationsKey = stringPreferencesKey("device_image_animations")
    // "name=seconds" comma list, only for durations the user changed here.
    private val animationDurationsKey = stringPreferencesKey("device_image_animation_durations")
    private val t2dEasyKey = intPreferencesKey("device_t2d_easy_seconds")
    private val t2dGoodKey = intPreferencesKey("device_t2d_good_seconds")
    private val d2tEasyKey = intPreferencesKey("device_d2t_easy_seconds")
    private val d2tGoodKey = intPreferencesKey("device_d2t_good_seconds")
    private val t2sEasyKey = intPreferencesKey("device_t2s_easy_seconds")
    private val t2sGoodKey = intPreferencesKey("device_t2s_good_seconds")

    val fontScale: StateFlow<Float> = dataStore.data
        .map { it[fontScaleKey] ?: DEFAULT_FONT_SCALE }
        .catch { emit(DEFAULT_FONT_SCALE) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_FONT_SCALE)

    val overrides: StateFlow<DeviceOverrides> = dataStore.data
        .map { prefs ->
            DeviceOverrides(
                accentColor = prefs[accentKey],
                showCardImages = prefs[showImagesKey],
                showImagesTermToDefinition = prefs[showImagesT2dKey],
                showImagesDefinitionToTerm = prefs[showImagesD2tKey],
                showImagesTermToSentence = prefs[showImagesT2sKey],
                imagePrefetchCount = prefs[prefetchKey],
                imageAnimations = prefs[animationsKey]?.let { stored ->
                    stored.split(',').map(String::trim).filter(String::isNotEmpty)
                },
                imageAnimationDurations = prefs[animationDurationsKey]
                    ?.split(',')
                    ?.mapNotNull { entry ->
                        val (name, seconds) = entry.split('=').takeIf { it.size == 2 }
                            ?: return@mapNotNull null
                        seconds.toDoubleOrNull()?.let { name to it }
                    }
                    ?.toMap()
                    ?: emptyMap(),
                termToDefinitionEasySeconds = prefs[t2dEasyKey],
                termToDefinitionGoodSeconds = prefs[t2dGoodKey],
                definitionToTermEasySeconds = prefs[d2tEasyKey],
                definitionToTermGoodSeconds = prefs[d2tGoodKey],
                termToSentenceEasySeconds = prefs[t2sEasyKey],
                termToSentenceGoodSeconds = prefs[t2sGoodKey],
            )
        }
        .catch { emit(DeviceOverrides()) }
        .stateIn(scope, SharingStarted.Eagerly, DeviceOverrides())

    fun setFontScale(value: Float) = set { it[fontScaleKey] = value }

    fun setAccentColor(value: String) = set { it[accentKey] = value }

    fun setShowCardImages(value: Boolean) = set { it[showImagesKey] = value }

    fun setShowImagesTermToDefinition(value: Boolean) = set { it[showImagesT2dKey] = value }

    fun setShowImagesDefinitionToTerm(value: Boolean) = set { it[showImagesD2tKey] = value }

    fun setShowImagesTermToSentence(value: Boolean) = set { it[showImagesT2sKey] = value }

    fun setImagePrefetchCount(value: Int) = set { it[prefetchKey] = value.coerceIn(0, 10) }

    fun setImageAnimations(names: List<String>) =
        set { it[animationsKey] = names.joinToString(",") }

    fun setImageAnimationDuration(name: String, seconds: Double) {
        scope.launch {
            dataStore.edit { prefs ->
                val current = prefs[animationDurationsKey]
                    ?.split(',')
                    ?.mapNotNull { entry ->
                        val parts = entry.split('=')
                        if (parts.size == 2) parts[0] to parts[1] else null
                    }
                    ?.toMap()
                    ?: emptyMap()
                val next = current + (name to seconds.coerceIn(0.5, 30.0).toString())
                prefs[animationDurationsKey] = next.entries.joinToString(",") { (n, s) -> "$n=$s" }
            }
        }
    }

    fun setTimingBand(direction: String, easy: Int, good: Int) = set {
        val (easyKey, goodKey) = when (direction) {
            "definition_to_term" -> d2tEasyKey to d2tGoodKey
            "term_to_sentence" -> t2sEasyKey to t2sGoodKey
            else -> t2dEasyKey to t2dGoodKey
        }
        it[easyKey] = easy.coerceIn(1, 600)
        it[goodKey] = good.coerceIn(2, 900)
    }

    private fun set(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch {
            dataStore.edit { transform(it) }
        }
    }

    companion object {
        const val DEFAULT_FONT_SCALE = 1.06f

        val TEXT_SCALES = listOf(
            "Compact" to 0.95f,
            "Default" to DEFAULT_FONT_SCALE,
            "Large" to 1.18f,
            "Extra large" to 1.30f,
        )
    }
}
