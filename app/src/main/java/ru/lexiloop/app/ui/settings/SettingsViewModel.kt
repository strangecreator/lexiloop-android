package ru.lexiloop.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lexiloop.app.data.api.ModelOption
import ru.lexiloop.app.data.api.SettingsDto
import ru.lexiloop.app.data.api.SettingsWriteBody
import ru.lexiloop.app.data.repo.ApiResult
import ru.lexiloop.app.data.repo.ContentRepository
import ru.lexiloop.app.data.repo.DeviceOverrides
import ru.lexiloop.app.data.repo.DevicePrefs
import ru.lexiloop.app.data.repo.SettingsStore
import ru.lexiloop.app.data.repo.ToastBus
import javax.inject.Inject

data class SettingsUiState(
    val form: SettingsDto = SettingsDto(),
    val models: List<ModelOption> = emptyList(),
    // Staged provider-key edits: non-empty replaces, "" removes on save.
    val stagedTokens: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val advancedOpen: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val settingsStore: SettingsStore,
    private val toastBus: ToastBus,
    private val devicePrefs: DevicePrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(form = settingsStore.settings.value))
    val state: StateFlow<SettingsUiState> = _state

    val fontScale: StateFlow<Float> = devicePrefs.fontScale

    /** Device-local study/appearance preferences, applied immediately. */
    val overrides: StateFlow<DeviceOverrides> = devicePrefs.overrides

    fun setFontScale(value: Float) = devicePrefs.setFontScale(value)

    fun setAccentColor(value: String) = devicePrefs.setAccentColor(value)

    fun setStudyDirections(directions: List<String>) = devicePrefs.setStudyDirections(directions)

    fun setShowCardImages(value: Boolean) = devicePrefs.setShowCardImages(value)

    fun setShowImagesTermToDefinition(value: Boolean) =
        devicePrefs.setShowImagesTermToDefinition(value)

    fun setShowImagesDefinitionToTerm(value: Boolean) =
        devicePrefs.setShowImagesDefinitionToTerm(value)

    fun setShowImagesTermToSentence(value: Boolean) =
        devicePrefs.setShowImagesTermToSentence(value)

    fun setImagePrefetchCount(value: Int) = devicePrefs.setImagePrefetchCount(value)

    fun setImageAnimations(names: List<String>) = devicePrefs.setImageAnimations(names)

    fun setImageAnimationDuration(name: String, seconds: Double) =
        devicePrefs.setImageAnimationDuration(name, seconds)

    fun setTimingBand(direction: String, easy: Int, good: Int) =
        devicePrefs.setTimingBand(direction, easy, good)

    init {
        viewModelScope.launch {
            when (val result = repository.models()) {
                is ApiResult.Success -> _state.update { it.copy(models = result.data) }
                is ApiResult.Error -> Unit // the picker shows a loading hint
            }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { server ->
                // Refresh the form when the server copy changes elsewhere
                // (theme toggle from the sidebar) and nothing is being edited.
                if (!_state.value.busy) _state.update { it.copy(form = server) }
            }
        }
    }

    fun patch(transform: (SettingsDto) -> SettingsDto) =
        _state.update { it.copy(form = transform(it.form)) }

    fun stageToken(provider: String, token: String) = _state.update {
        it.copy(stagedTokens = it.stagedTokens + (provider to token))
    }

    fun unstageToken(provider: String) = _state.update {
        it.copy(stagedTokens = it.stagedTokens - provider)
    }

    fun toggleAdvanced() = _state.update { it.copy(advancedOpen = !it.advancedOpen) }

    fun save() {
        val current = _state.value
        if (current.busy) return
        _state.update { it.copy(busy = true) }
        val f = current.form
        val body = SettingsWriteBody(
            theme = f.theme,
            generationModel = f.generationModel,
            judgeModel = f.judgeModel,
            imageModel = f.imageModel,
            judgeAcceptanceScore = f.judgeAcceptanceScore,
            sentenceJudgeModel = f.sentenceJudgeModel,
            sentenceAcceptanceScore = f.sentenceAcceptanceScore,
            dailyNewLimit = f.dailyNewLimit,
            learningStepsMinutes = f.learningStepsMinutes,
            relearningStepsMinutes = f.relearningStepsMinutes,
            graduatingIntervalDays = f.graduatingIntervalDays,
            easyIntervalDays = f.easyIntervalDays,
            easyBonus = f.easyBonus,
            hardMultiplier = f.hardMultiplier,
            lapseMultiplier = f.lapseMultiplier,
            minimumEase = f.minimumEase,
            providerTokens = current.stagedTokens.takeIf { it.isNotEmpty() },
        )
        viewModelScope.launch {
            when (val result = repository.saveSettings(body)) {
                is ApiResult.Success -> {
                    settingsStore.update(result.data)
                    _state.update { it.copy(busy = false, form = result.data, stagedTokens = emptyMap()) }
                    toastBus.success("Settings saved")
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(busy = false) }
                    toastBus.error(result.message)
                }
            }
        }
    }
}
