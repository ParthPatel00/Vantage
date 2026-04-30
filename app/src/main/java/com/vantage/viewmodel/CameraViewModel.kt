package com.vantage.viewmodel

import androidx.lifecycle.ViewModel
import com.vantage.models.CameraUiState
import com.vantage.models.FilterType
import com.vantage.models.UnsplashPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun onAIButtonTapped() {
        // Phase 2: starts/stops the AI coaching analysis loop
    }

    fun onModeToggled() {
        // Phase 2: toggles between DO_IT_FOR_ME and COACH_ME
    }

    fun onFilterSelected(filter: FilterType) {
        // Phase 2: applies selected filter to camera preview
    }

    fun onInspoPhotoSelected(photo: UnsplashPhoto) {
        // Phase 2: sends inspo photo to AI for style matching
    }

    fun onMicButtonHeld() {
        // Phase 2: starts speech recognition
    }

    fun onMicButtonReleased() {
        // Phase 2: stops speech recognition
    }

    fun onManualShutter() {
        // Phase 2: captures photo immediately
    }

    fun onCountdownComplete() {
        // Phase 2: fires auto-capture after 3-2-1 countdown
    }
}
