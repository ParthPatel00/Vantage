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

    // Screen observes this to capture a preview frame and call onPreviewFrameCaptured()
    private val _aiCaptureSignal = MutableStateFlow(0)
    val aiCaptureSignal: StateFlow<Int> = _aiCaptureSignal.asStateFlow()

    // Screen observes this to take the final gallery photo
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
        Log.d("Vantage", "AI shoot started")
    }

    fun onPreviewFrameCaptured(framePath: String) {
        viewModelScope.launch {
            try {
                Log.d("Vantage", "Sending frame to Gemma (iteration=$analysisIteration)")
                val userIntent = _uiState.value.voicePrompt.ifBlank { null }
                val analysis = if (gemmaEngine.isReady()) {
                    gemmaEngine.analyzeScene(framePath, analysisIteration, lastAnalysis, userIntent)
                } else {
                    Log.w("Vantage", "Gemma not ready — using defaults and shooting")
                    SceneAnalysis(ready = true, reasoning = "AI warming up")
                }

                Log.d("Vantage", "Gemma result: ready=${analysis.ready} reason=${analysis.reasoning}")
                lastAnalysis = analysis
                _pendingAnalysis.value = analysis

                val roundMsg = buildString {
                    if (analysis.sceneDescription.isNotBlank()) append(analysis.sceneDescription)
                    if (analysis.reasoning.isNotBlank()) {
                        if (isNotBlank()) append(" | ")
                        append(analysis.reasoning)
                    }
                }

                _uiState.update {
                    it.copy(
                        currentFilter = analysis.filter,
                        currentZoom = analysis.zoom,
                        brightness = analysis.brightness,
                        contrast = analysis.contrast,
                        saturation = analysis.saturation,
                        gamma = analysis.gamma,
                        analysisIteration = analysisIteration,
                        aiReasoning = analysis.reasoning,
                        subjectBox = analysis.subjectBox,
                        suggestedBox = analysis.suggestedBox,
                        compositionTip = analysis.compositionTip,
                        compositionOk = analysis.compositionOk,
                        sceneDescription = analysis.sceneDescription,
                        photographyTip = analysis.photographyTip,
                        aiMessages = it.aiMessages + listOfNotNull(roundMsg.ifBlank { null })
                    )
                }

                val shouldShoot = analysis.ready || analysisIteration >= 2
                if (shouldShoot) {
                    isLoopActive = false
                    _uiState.update { it.copy(isAiActive = false) }
                    delay(400)
                    _photoSignal.update { it + 1 }
                    analysisIteration = 0
                    lastAnalysis = null
                } else {
                    analysisIteration++
                    _uiState.update { it.copy(analysisIteration = analysisIteration) }
                    delay(800)
                    _aiCaptureSignal.update { it + 1 }
                }
            } catch (e: Exception) {
                Log.e("Vantage", "Analysis loop error", e)
                isLoopActive = false
                analysisIteration = 0
                _uiState.update { it.copy(isAiActive = false) }
                _photoSignal.update { it + 1 }
            }
        }
    }

    fun onPhotoCaptured(uri: Uri) {
        Log.d("Vantage", "Photo saved: $uri")
        _uiState.update { it.copy(lastCapturedUri = uri) }
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
