package com.vantage.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vantage.ai.GemmaEngine
import com.vantage.ai.SceneAnalysis
import com.vantage.camera.standard.AspectRatioManager
import com.vantage.models.CameraUiState
import com.vantage.models.FilterType
import com.vantage.models.FlashMode
import kotlinx.coroutines.Job
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

    private var isLoopActive = false
    private var analysisIteration = 0
    private var lastAnalysis: SceneAnalysis? = null
    private var lockedFilter: FilterType? = null
    private var timeoutJob: Job? = null

    // Timestamp shared between original and enhanced saves for filename pairing
    private val _captureTimestamp = MutableStateFlow(0L)
    val captureTimestamp: StateFlow<Long> = _captureTimestamp.asStateFlow()

    // True only for the very first frame capture after shutter press
    private val _saveOriginal = MutableStateFlow(false)
    val saveOriginal: StateFlow<Boolean> = _saveOriginal.asStateFlow()

    // Screen observes this to capture a preview frame and call onPreviewFrameCaptured()
    private val _aiCaptureSignal = MutableStateFlow(0)
    val aiCaptureSignal: StateFlow<Int> = _aiCaptureSignal.asStateFlow()

    // Screen observes this to take the final enhanced photo
    private val _photoSignal = MutableStateFlow(0)
    val photoSignal: StateFlow<Int> = _photoSignal.asStateFlow()

    // Screen observes this to apply Camera2 parameters (ISO, shutter, WB, etc.)
    private val _pendingAnalysis = MutableStateFlow<SceneAnalysis?>(null)
    val pendingAnalysis: StateFlow<SceneAnalysis?> = _pendingAnalysis.asStateFlow()

    init {
        viewModelScope.launch { gemmaEngine.initialize(application) }
    }

    fun onAiShutterTapped() {
        if (isLoopActive) return
        isLoopActive = true
        analysisIteration = 0
        lastAnalysis = null
        lockedFilter = null
        _captureTimestamp.value = System.currentTimeMillis()
        _saveOriginal.value = true
        _uiState.update {
            it.copy(
                isAiActive = true, analysisIteration = 0, aiReasoning = "",
                subjectBox = emptyList(), suggestedBox = emptyList(),
                compositionTip = "", compositionOk = true,
                sceneDescription = "", photographyTip = "",
                aiMessages = emptyList()
            )
        }
        _aiCaptureSignal.update { it + 1 }
        Log.d("Vantage", "AI shoot started, timestamp=${_captureTimestamp.value}")
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(10_000)
            if (isLoopActive) {
                Log.w("Vantage", "AI analysis timed out after 10s, shooting with defaults")
                isLoopActive = false
                _pendingAnalysis.value = SceneAnalysis(ready = true)
                _uiState.update { it.copy(isAiActive = false) }
                delay(200)
                _photoSignal.update { it + 1 }
            }
        }
    }

    fun onOriginalSaved() {
        _saveOriginal.value = false
    }

    fun onPreviewFrameCaptured(framePath: String) {
        if (!isLoopActive) {
            Log.d("Vantage", "Loop no longer active, ignoring frame")
            return
        }
        viewModelScope.launch {
            try {
                Log.d("Vantage", "Sending frame to Gemma (iteration=$analysisIteration)")
                val userIntent = _uiState.value.voicePrompt.ifBlank { null }
                val analysis = if (gemmaEngine.isReady()) {
                    gemmaEngine.analyzeScene(framePath, analysisIteration, lastAnalysis, userIntent)
                } else {
                    Log.w("Vantage", "Gemma not ready, using defaults")
                    SceneAnalysis(ready = true, reasoning = "AI warming up")
                }

                if (!isLoopActive) {
                    Log.d("Vantage", "Loop cancelled during analysis, discarding result")
                    return@launch
                }

                Log.d("Vantage", "Gemma result: ready=${analysis.ready} filter=${analysis.filter} reason=${analysis.reasoning}")

                // Lock the filter from round 1 so round 2 can't override the creative decision
                val correctedAnalysis = if (analysisIteration == 0) {
                    lockedFilter = analysis.filter
                    analysis
                } else if (lockedFilter != null && lockedFilter != FilterType.NATURAL && analysis.filter != lockedFilter) {
                    Log.d("Vantage", "Round ${analysisIteration} tried to change filter to ${analysis.filter}, keeping locked filter $lockedFilter")
                    analysis.copy(filter = lockedFilter!!)
                } else {
                    analysis
                }

                lastAnalysis = correctedAnalysis
                _pendingAnalysis.value = correctedAnalysis

                val roundMsg = buildString {
                    if (correctedAnalysis.sceneDescription.isNotBlank()) append(correctedAnalysis.sceneDescription)
                    if (correctedAnalysis.reasoning.isNotBlank()) {
                        if (isNotBlank()) append(" | ")
                        append(correctedAnalysis.reasoning)
                    }
                }

                _uiState.update {
                    it.copy(
                        currentFilter = correctedAnalysis.filter,
                        currentZoom = correctedAnalysis.zoom,
                        brightness = correctedAnalysis.brightness,
                        contrast = correctedAnalysis.contrast,
                        saturation = correctedAnalysis.saturation,
                        gamma = correctedAnalysis.gamma,
                        analysisIteration = analysisIteration,
                        aiReasoning = correctedAnalysis.reasoning,
                        subjectBox = correctedAnalysis.subjectBox,
                        suggestedBox = correctedAnalysis.suggestedBox,
                        compositionTip = correctedAnalysis.compositionTip,
                        compositionOk = correctedAnalysis.compositionOk,
                        sceneDescription = correctedAnalysis.sceneDescription,
                        photographyTip = correctedAnalysis.photographyTip,
                        aiMessages = it.aiMessages + listOfNotNull(roundMsg.ifBlank { null })
                    )
                }

                isLoopActive = false
                timeoutJob?.cancel()
                _uiState.update { it.copy(isAiActive = false) }
                delay(200)
                _photoSignal.update { it + 1 }
                analysisIteration = 0
                lastAnalysis = null
                viewModelScope.launch {
                    delay(1500)
                    _pendingAnalysis.value = null
                    _uiState.update {
                        it.copy(
                            subjectBox = emptyList(),
                            suggestedBox = emptyList(),
                            compositionTip = "",
                            sceneDescription = "",
                            photographyTip = ""
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("Vantage", "Analysis loop error", e)
                isLoopActive = false
                analysisIteration = 0
                _uiState.update { it.copy(isAiActive = false) }
            }
        }
    }

    fun onPhotoCaptured(uri: Uri) {
        Log.d("Vantage", "Photo saved: $uri")
        _uiState.update { it.copy(lastCapturedUri = uri) }
    }

    fun cancelAiLoop() {
        if (isLoopActive) {
            Log.d("Vantage", "AI loop cancelled")
            isLoopActive = false
            timeoutJob?.cancel()
            analysisIteration = 0
            lastAnalysis = null
            _saveOriginal.value = false
            _uiState.update { it.copy(isAiActive = false) }
        }
    }

    fun onFlashToggled() {
        _uiState.update {
            it.copy(flashMode = when (it.flashMode) {
                FlashMode.OFF -> FlashMode.ON
                FlashMode.ON -> FlashMode.AUTO
                FlashMode.AUTO -> FlashMode.OFF
            })
        }
    }

    fun onRatioToggled() {
        _uiState.update {
            it.copy(currentRatio = when (it.currentRatio) {
                AspectRatioManager.AspectRatio.RATIO_4_3  -> AspectRatioManager.AspectRatio.RATIO_16_9
                AspectRatioManager.AspectRatio.RATIO_16_9 -> AspectRatioManager.AspectRatio.RATIO_1_1
                AspectRatioManager.AspectRatio.RATIO_1_1  -> AspectRatioManager.AspectRatio.RATIO_4_3
            })
        }
    }

    fun onZoomSelected(zoom: Float) {
        _uiState.update { it.copy(currentZoom = zoom) }
    }

    fun onCameraFlipped() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun onFilterSelected(filter: FilterType) {
        _uiState.update { it.copy(currentFilter = filter) }
    }

    fun onListeningStarted() {
        _uiState.update { it.copy(isListening = true) }
    }

    fun onVoiceResult(text: String) {
        _uiState.update { it.copy(isListening = false, voicePrompt = text) }
        Log.d("Vantage", "Voice prompt: $text")
    }

    fun onVoiceCancelled() {
        _uiState.update { it.copy(isListening = false) }
    }

    fun onVoicePromptCleared() {
        _uiState.update { it.copy(voicePrompt = "") }
    }

    override fun onCleared() {
        gemmaEngine.close()
        super.onCleared()
    }
}
