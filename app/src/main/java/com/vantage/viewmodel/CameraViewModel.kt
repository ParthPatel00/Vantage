package com.vantage.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vantage.ai.GemmaEngine
import com.vantage.models.AppMode
import com.vantage.models.CameraUiState
import com.vantage.models.ChatMessage
import com.vantage.models.FilterType
import com.vantage.models.FlashMode
import com.vantage.models.UnsplashPhoto
import com.vantage.voice.VoiceSystem
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

    init {
        viewModelScope.launch {
            gemmaEngine.initialize(application)
        }
    }

    fun onCaptureButtonTapped(framePath: String) {
        viewModelScope.launch {
            Log.d("Vantage", "Sending frame to Gemma for analysis...")
            val description = gemmaEngine.describeImage(framePath)
            Log.d("Vantage", "Gemma response: $description")
            
            // AI Speaks the response
            voiceSystem.speak(description)
            
            _uiState.update {
                it.copy(chatMessages = it.chatMessages + ChatMessage(description, isFromUser = false))
            }
        }
    }

    fun onAIButtonTapped() {}

    fun onModeToggled() {
        _uiState.update {
            it.copy(
                appMode = if (it.appMode == AppMode.DO_IT_FOR_ME)
                    AppMode.COACH_ME else AppMode.DO_IT_FOR_ME
            )
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

    override fun onCleared() {
        super.onCleared()
        voiceSystem.shutdown()
    }

    fun onManualShutter() {}
    fun onCountdownComplete() {}
}
