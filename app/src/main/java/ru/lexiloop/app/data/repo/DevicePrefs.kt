package ru.lexiloop.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.lexiloop.app.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences that belong to this device rather than the account — currently
 * the interface text scale. Server settings stay untouched.
 */
@Singleton
class DevicePrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val fontScaleKey = floatPreferencesKey("font_scale")

    val fontScale: StateFlow<Float> = dataStore.data
        .map { it[fontScaleKey] ?: DEFAULT_FONT_SCALE }
        .catch { emit(DEFAULT_FONT_SCALE) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_FONT_SCALE)

    fun setFontScale(value: Float) {
        scope.launch {
            dataStore.edit { it[fontScaleKey] = value }
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
