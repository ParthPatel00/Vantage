package com.vantage.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vantage.ai.GemmaEngine
import com.vantage.camera.standard.AspectRatioManager
import com.vantage.camera.standard.LensSwitchingProvider
import com.vantage.camera.standard.StandardCameraManager
import com.vantage.camera.standard.AdvancedParameterHandler
import com.vantage.models.AppMode
import com.vantage.models.CameraUiState
import com.vantage.models.ChatMessage
import com.vantage.models.CoachingResult
import com.vantage.models.FilterType
import com.vantage.models.FlashMode
import com.vantage.models.UnsplashPhoto
import com.vantage.settings.AdvancedSettingsRegistry
import com.vantage.voice.VoiceSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val gemmaEngine = GemmaEngine()
    private val voiceSystem = VoiceSystem(application)

    private var isCoachingInFlight = false
    private var currentCoachStep: String? = null

    // Increments each time the ViewModel wants the screen to take one coaching frame.
    private val _coachCaptureSignal = MutableStateFlow(0)
    val coachCaptureSignal: StateFlow<Int> = _coachCaptureSignal.asStateFlow()
    
    // Standard Camera Modules
    val cameraManager = StandardCameraManager(application)
    private val lensProvider = LensSwitchingProvider(application)
    
    // Pro State
    private val _availableLenses = MutableStateFlow<List<LensSwitchingProvider.LensInfo>>(emptyList())
    val availableLenses: StateFlow<List<LensSwitchingProvider.LensInfo>> = _availableLenses.asStateFlow()
    
    private val _selectedLens = MutableStateFlow<LensSwitchingProvider.LensInfo?>(null)
    val selectedLens: StateFlow<LensSwitchingProvider.LensInfo?> = _selectedLens.asStateFlow()
    
    private val _currentRatio = MutableStateFlow(AspectRatioManager.AspectRatio.RATIO_4_3)
    val currentRatio: StateFlow<AspectRatioManager.AspectRatio> = _currentRatio.asStateFlow()

    private val _manualSettings = MutableStateFlow(AdvancedParameterHandler.ManualSettings(zoomRatio = 1.0f))
    val manualSettings: StateFlow<AdvancedParameterHandler.ManualSettings> = _manualSettings.asStateFlow()

    // Dynamic Settings State
    private val _settingsValues = MutableStateFlow<Map<AdvancedSettingsRegistry.SettingType, Float>>(
        AdvancedSettingsRegistry.ALL_SETTINGS.associate { it.type to it.defaultValue }
    )
    val settingsValues: StateFlow<Map<AdvancedSettingsRegistry.SettingType, Float>> = _settingsValues.asStateFlow()

    private val _activeAdjustment = MutableStateFlow(AdvancedSettingsRegistry.SettingType.NONE)
    val activeAdjustment: StateFlow<AdvancedSettingsRegistry.SettingType> = _activeAdjustment.asStateFlow()
    
    private val _isFilterListVisible = MutableStateFlow(false)
    val isFilterListVisible: StateFlow<Boolean> = _isFilterListVisible.asStateFlow()

    // Convenience computed values for OpenGL
    val brightness: StateFlow<Float> get() = MutableStateFlow(_settingsValues.value[AdvancedSettingsRegistry.SettingType.BRIGHTNESS] ?: 0f)
    val contrast: StateFlow<Float> get() = MutableStateFlow(_settingsValues.value[AdvancedSettingsRegistry.SettingType.CONTRAST] ?: 1f)
    val saturation: StateFlow<Float> get() = MutableStateFlow(_settingsValues.value[AdvancedSettingsRegistry.SettingType.SATURATION] ?: 1f)
    val gamma: StateFlow<Float> get() = MutableStateFlow(_settingsValues.value[AdvancedSettingsRegistry.SettingType.GAMMA] ?: 1f)

    init {
        viewModelScope.launch {
            gemmaEngine.initialize(application)
            cameraManager.startBackgroundThread()
            refreshLenses()
        }
    }

    private fun refreshLenses() {
        val lenses = lensProvider.getAvailableLenses()
        _availableLenses.value = lenses
        if (_selectedLens.value == null) {
            _selectedLens.value = lenses.find { it.facing == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK } ?: lenses.firstOrNull()
        }
    }

    fun onLensSelected(lens: LensSwitchingProvider.LensInfo) {
        _selectedLens.value = lens
    }

    fun onRatioToggled() {
        _currentRatio.value = when(_currentRatio.value) {
            AspectRatioManager.AspectRatio.RATIO_4_3 -> AspectRatioManager.AspectRatio.RATIO_16_9
            AspectRatioManager.AspectRatio.RATIO_16_9 -> AspectRatioManager.AspectRatio.RATIO_1_1
            AspectRatioManager.AspectRatio.RATIO_1_1 -> AspectRatioManager.AspectRatio.RATIO_4_3
        }
    }

    fun onAdjustmentChanged(type: AdvancedSettingsRegistry.SettingType, value: Float) {
        _settingsValues.update { it + (type to value) }
        
        val definition = AdvancedSettingsRegistry.ALL_SETTINGS.find { it.type == type } ?: return
        if (!definition.isOpenGL) {
            applyCameraSetting(type, value)
        }
    }

    private fun applyCameraSetting(type: AdvancedSettingsRegistry.SettingType, value: Float) {
        val current = _manualSettings.value
        val next = when(type) {
            AdvancedSettingsRegistry.SettingType.EV -> current.copy(exposureComp = value)
            AdvancedSettingsRegistry.SettingType.ISO -> current.copy(iso = value.toInt())
            AdvancedSettingsRegistry.SettingType.SHUTTER -> current.copy(shutterSpeedNs = (1_000_000_000L / value.toLong()))
            AdvancedSettingsRegistry.SettingType.FOCUS -> current.copy(focusDistance = value)
            AdvancedSettingsRegistry.SettingType.WB -> current.copy(whiteBalanceMode = value.toInt())
            AdvancedSettingsRegistry.SettingType.SHARPNESS -> current.copy(sharpness = value.toInt())
            AdvancedSettingsRegistry.SettingType.NOISE_REDUCTION -> current.copy(denoiseMode = value.toInt())
            else -> current
        }
        onManualSettingChanged(next)
    }

    fun onActiveAdjustmentChanged(type: AdvancedSettingsRegistry.SettingType) {
        _activeAdjustment.value = type
        if (type != AdvancedSettingsRegistry.SettingType.NONE) {
            _isFilterListVisible.value = false
        }
    }

    fun onFilterToggleTapped() {
        _isFilterListVisible.update { !it }
        if (_isFilterListVisible.value) {
            _activeAdjustment.value = AdvancedSettingsRegistry.SettingType.NONE
        }
    }

    fun onFilterSelected(filter: FilterType) {
        _uiState.update { it.copy(currentFilter = filter) }
    }

    fun onManualSettingChanged(settings: AdvancedParameterHandler.ManualSettings) {
        _manualSettings.value = settings
        _selectedLens.value?.let {
            cameraManager.updateSettings(settings, it.logicalId)
        }
    }

    fun onZoomChanged(zoom: Float) {
        onManualSettingChanged(_manualSettings.value.copy(zoomRatio = zoom))
    }

    fun setCoachingResult(result: CoachingResult) {
        _uiState.update {
            it.copy(
                pendingUserActions = result.userActions,
                currentFilter = result.filter,
                readyToCapture = result.readyToCapture
            )
        }
    }

    fun onCaptureButtonTapped(framePath: String) {
        currentCoachStep = null
        _uiState.update { it.copy(readyToCapture = false, pendingUserActions = emptyList()) }
        if (_uiState.value.appMode == AppMode.COACH_ME) {
            scheduleNextCoachCapture(delayMs = 3000)
        }

        viewModelScope.launch {
            Log.d("Vantage", "Sending frame to Gemma for analysis...")
            val description = gemmaEngine.describeImage(framePath)
            Log.d("Vantage", "Gemma response: $description")
            _uiState.update {
                it.copy(chatMessages = it.chatMessages + ChatMessage(description, isFromUser = false))
            }
        }
    }

    fun onCoachingFrame(path: String) {
        if (isCoachingInFlight) return
        if (_uiState.value.readyToCapture) return
        if (!gemmaEngine.isReady()) {
            scheduleNextCoachCapture(delayMs = 5000)
            return
        }
        isCoachingInFlight = true
        viewModelScope.launch {
            try {
                val signal = gemmaEngine.getCoachingStep(path, currentCoachStep, _uiState.value.coachingSubject)
                val stepChanged = signal.step != currentCoachStep
                if (stepChanged) {
                    currentCoachStep = signal.step
                    if (_uiState.value.voiceCoachEnabled) {
                        voiceSystem.speak(signal.step)
                    }
                }
                _uiState.update {
                    it.copy(
                        pendingUserActions = listOf(signal.step),
                        readyToCapture = signal.readyToCapture,
                        coachRevision = it.coachRevision + 1,
                        lastCoachDebug = "PROMPT:\n${signal.prompt}\n\nRESPONSE:\n${signal.rawResponse}"
                    )
                }
            } finally {
                isCoachingInFlight = false
                scheduleNextCoachCapture(delayMs = 2000)
            }
        }
    }

    private fun scheduleNextCoachCapture(delayMs: Long = 0) {
        viewModelScope.launch {
            delay(delayMs)
            if (_uiState.value.appMode == AppMode.COACH_ME && !_uiState.value.readyToCapture) {
                _coachCaptureSignal.update { it + 1 }
            }
        }
    }

    fun onVoiceCoachToggled() {
        _uiState.update { it.copy(voiceCoachEnabled = !it.voiceCoachEnabled) }
    }

    fun onCoachingSubjectChanged(subject: String) {
        _uiState.update { it.copy(coachingSubject = subject) }
    }

    fun onAIButtonTapped() {}

    fun onModeToggled() {
        val nextMode = if (_uiState.value.appMode == AppMode.DO_IT_FOR_ME)
            AppMode.COACH_ME else AppMode.DO_IT_FOR_ME
        currentCoachStep = null
        _uiState.update {
            it.copy(
                appMode = nextMode,
                pendingUserActions = emptyList(),
                readyToCapture = false
            )
        }
        if (nextMode == AppMode.COACH_ME) {
            scheduleNextCoachCapture(delayMs = 1500)
        }
    }

    fun onInspoPhotoSelected(photo: UnsplashPhoto) {}

    fun onFlashToggled() {
        _uiState.update {
            val nextMode = when (it.flashMode) {
                FlashMode.OFF -> FlashMode.ON
                FlashMode.ON -> FlashMode.AUTO
                FlashMode.AUTO -> FlashMode.OFF
            }
            it.copy(flashMode = nextMode)
        }
    }

    fun onMicButtonToggled() {
        if (_uiState.value.isListening) {
            voiceSystem.stopListening()
            _uiState.update { it.copy(isListening = false) }
        } else {
            _uiState.update { it.copy(isListening = true) }
            voiceSystem.startListening(
                onResult = { text ->
                    _uiState.update {
                        it.copy(
                            isListening = false,
                            chatMessages = it.chatMessages + ChatMessage(text, isFromUser = true)
                        )
                    }
                },
                onError = {
                    _uiState.update { it.copy(isListening = false) }
                }
            )
        }
    }

    fun onManualShutter() {
        // Shutter logic using StandardCameraManager can be added here
        Log.d("Vantage", "Manual shutter tapped")
    }

    fun onCountdownComplete() {}

    override fun onCleared() {
        gemmaEngine.close()
        voiceSystem.shutdown()
        cameraManager.stopBackgroundThread()
        super.onCleared()
    }
}
