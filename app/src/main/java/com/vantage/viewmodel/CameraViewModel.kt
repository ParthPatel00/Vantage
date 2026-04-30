package com.vantage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vantage.models.CameraUiState
import com.vantage.models.CoachingResult
import com.vantage.models.FilterType
import com.vantage.models.UnsplashPhoto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var fakeCoachJob: Job? = null

    init {
        startFakeCoachLoop()
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

    fun onAIButtonTapped() {}
    fun onModeToggled() {}
    fun onFilterSelected(filter: FilterType) {}
    fun onInspoPhotoSelected(photo: UnsplashPhoto) {}
    fun onMicButtonHeld() {}
    fun onMicButtonReleased() {}
    fun onManualShutter() {}
    fun onCountdownComplete() {}

    override fun onCleared() {
        fakeCoachJob?.cancel()
        super.onCleared()
    }

    private fun startFakeCoachLoop() {
        fakeCoachJob?.cancel()
        fakeCoachJob = viewModelScope.launch {
            // Fake the model-load animation so the loading screen yields to the camera screen.
            val steps = 20
            repeat(steps) { i ->
                _uiState.update { it.copy(modelLoadProgress = (i + 1f) / steps) }
                delay(60)
            }
            _uiState.update { it.copy(modelLoaded = true, isCoachingActive = true) }

            val script = listOf(
                "Tilt the camera down a bit",
                "A little more — almost there",
                "Step back two paces",
                "Move slightly to the left",
                "Hold still",
                "Got it — that turned out great"
            )
            var i = 0
            while (true) {
                setCoachingResult(CoachingResult(userActions = listOf(script[i % script.size])))
                delay(4_000)
                i++
            }
        }
    }
}
