package com.vantage.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vantage.ai.GemmaEngine
import com.vantage.models.AppMode
import com.vantage.models.CameraUiState
import com.vantage.models.ChatMessage
import com.vantage.models.CoachingResult
import com.vantage.models.FilterType
import com.vantage.models.FlashMode
import com.vantage.models.UnsplashPhoto
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
    // Using a counter (not Boolean) so consecutive signals are always distinct values.
    private val _coachCaptureSignal = MutableStateFlow(0)
    val coachCaptureSignal: StateFlow<Int> = _coachCaptureSignal.asStateFlow()

    init {
        viewModelScope.launch {
            gemmaEngine.initialize(application)
        }
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
        Log.d("Vantage", "onCoachingFrame: inFlight=$isCoachingInFlight ready=${gemmaEngine.isReady()} path=$path")
        if (isCoachingInFlight) return
        if (_uiState.value.readyToCapture) return
        if (!gemmaEngine.isReady()) {
            // Engine still loading — retry after a longer delay
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
                // Always bump revision so the bubble flashes even when the step repeats,
                // letting the user know Gemma re-analyzed the scene.
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

    fun onFilterSelected(filter: FilterType) {}
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
            Log.d("Vantage", "Stopping voice listener")
            voiceSystem.stopListening()
            _uiState.update { it.copy(isListening = false) }
        } else {
            Log.d("Vantage", "Starting voice listener")
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

    fun onManualShutter() {}
    fun onCountdownComplete() {}

    override fun onCleared() {
        gemmaEngine.close()
        voiceSystem.shutdown()
        super.onCleared()
    }
}
