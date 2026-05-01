package com.vantage.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vantage.ai.CameraToolSet
import com.vantage.ai.GemmaEngine
import com.vantage.ai.ToolResult
import com.vantage.api.UnsplashClientImpl
import com.vantage.contracts.IUnsplashClient
import com.vantage.models.AppMode
import com.vantage.models.CameraUiState
import com.vantage.models.ChatMessage
import com.vantage.models.CoachingResult
import com.vantage.models.FilterType
import com.vantage.models.FlashMode
import com.vantage.models.UnsplashPhoto
import com.vantage.voice.VoiceSystem
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
    private val voiceSystem = VoiceSystem(application)
    private val unsplashClient: IUnsplashClient = UnsplashClientImpl()
    private val cameraToolSet = CameraToolSet(unsplashClient)

    private var fakeCoachJob: Job? = null
    private var lastCapturedFramePath: String? = null

    init {
        viewModelScope.launch {
            gemmaEngine.initialize(application)
        }
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

    fun onCaptureButtonTapped(framePath: String) {
        lastCapturedFramePath = framePath
        viewModelScope.launch {
            if (!gemmaEngine.isReady()) {
                Log.d(TAG, "Capture: Gemma not ready yet, skipping describeImage")
                return@launch
            }
            Log.d(TAG, "Sending frame to Gemma for analysis...")
            val description = gemmaEngine.describeImage(framePath)
            Log.d(TAG, "Gemma response: $description")
            if (isUsableModelResponse(description)) {
                addAiMessage(description, speak = true)
            }
        }
    }

    private fun isUsableModelResponse(text: String): Boolean =
        text.isNotBlank() &&
            !text.startsWith("Error:") &&
            text != "Engine not ready" &&
            text != "No response"

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

    fun onInspoPhotoSelected(photo: UnsplashPhoto) {
        _uiState.update { it.copy(selectedInspoPhoto = photo) }
        addAiMessage("Great reference. I'll help you recreate the pose and framing.", speak = true)

        // If we have a recently captured preview frame, ask Gemma to coach the user toward
        // recreating the reference. Phase 2 will replace this with a proper matchInspoStyle
        // path that returns a structured CoachingResult; for now we surface the model's
        // free-form response as a follow-up chat bubble.
        val framePath = lastCapturedFramePath ?: return
        val refDescription = photo.altDescription.ifBlank { "selected Unsplash inspiration photo" }
        val prompt = """
            The user selected this inspiration reference: $refDescription.
            Use the current camera frame and coach the user to recreate the human pose,
            framing, background alignment, and mood. Reply in 2-3 short sentences,
            casual photographer-friend tone.
        """.trimIndent()
        viewModelScope.launch {
            if (!gemmaEngine.isReady()) {
                Log.d(TAG, "Inspo coaching: Gemma not ready, skipping recreate-pose call")
                return@launch
            }
            try {
                val coachLine = gemmaEngine.queryWithImage(framePath, prompt)
                if (isUsableModelResponse(coachLine)) {
                    addAiMessage(coachLine, speak = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Inspo coaching call failed: ${e.message}")
            }
        }
    }

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
            Log.d(TAG, "Stopping voice listener")
            voiceSystem.stopListening()
            _uiState.update { it.copy(isListening = false) }
        } else {
            Log.d(TAG, "Starting voice listener")
            _uiState.update { it.copy(isListening = true) }
            voiceSystem.startListening(
                onResult = { text ->
                    _uiState.update {
                        it.copy(
                            isListening = false,
                            chatMessages = it.chatMessages + ChatMessage(text, isFromUser = true)
                        )
                    }
                    handleVoiceTranscript(text)
                },
                onError = {
                    _uiState.update { it.copy(isListening = false) }
                }
            )
        }
    }

    fun onManualShutter() {}
    fun onCountdownComplete() {}

    /**
     * Fire the inspiration tool directly without going through STT. Useful for demos
     * on the emulator where the host-mic toggle / language pack / network conditions
     * make Android `SpeechRecognizer` unreliable. Long-press the mic button to invoke.
     */
    fun triggerPoseInspiration() {
        val transcript = "find me poses to do"
        _uiState.update {
            it.copy(chatMessages = it.chatMessages + ChatMessage(transcript, isFromUser = true))
        }
        launchInspirationFallback(transcript)
    }

    override fun onCleared() {
        fakeCoachJob?.cancel()
        voiceSystem.shutdown()
        gemmaEngine.close()
        super.onCleared()
    }

    /**
     * Demo-reliability fallback for voice routing. Once full Gemma tool-calling lands,
     * every transcript should be sent through `CoachingSession` and Gemma decides
     * whether to invoke `findInspirationPhotos`. Until then, we detect a small set of
     * inspiration phrases and dispatch the tool ourselves with a pose-focused query.
     */
    private fun handleVoiceTranscript(text: String) {
        val normalized = text.lowercase().trim()
        if (INSPIRATION_PHRASES.any { normalized.contains(it) }) {
            launchInspirationFallback(text)
        }
        // TODO Phase 2: route non-inspiration transcripts to Gemma via
        // CoachingSession.handleVoiceCommand and apply the returned CoachingResult.
    }

    private fun launchInspirationFallback(transcript: String) {
        val query = buildPoseQuery(transcript)
        val voiceMessage =
            "I'll pull some references that fit this scene. Tap one and I'll help you recreate it."
        viewModelScope.launch {
            val result = cameraToolSet.findInspirationPhotos(
                userRequest = transcript,
                sceneDescription = "",
                inspirationType = "human_pose",
                unsplashQuery = query,
                voiceMessage = voiceMessage
            )
            applyInspirationToolResult(result)
        }
    }

    private fun applyInspirationToolResult(result: ToolResult.Inspiration) {
        _uiState.update {
            it.copy(inspoPhotos = result.photos, selectedInspoPhoto = null)
        }
        addAiMessage(result.voiceMessage, speak = true)
    }

    private fun addAiMessage(text: String, speak: Boolean) {
        if (text.isBlank()) return
        _uiState.update {
            it.copy(chatMessages = it.chatMessages + ChatMessage(text, isFromUser = false))
        }
        if (speak) {
            try { voiceSystem.speak(text) } catch (_: Exception) { /* TTS failure is non-fatal */ }
        }
    }

    /**
     * Build a pose-focused Unsplash query from the raw transcript. Pulled out into a
     * pure function so it stays easy to tune. Order of token concatenation is intentional:
     * subject → action → environment → style → "portrait pose photography reference" tail.
     */
    private fun buildPoseQuery(transcript: String): String {
        val t = transcript.lowercase()
        val tokens = mutableListOf<String>()

        when {
            "couple" in t -> tokens += "couple"
            "group" in t -> tokens += "group"
            "kids" in t || "children" in t || "family" in t -> tokens += "family"
        }
        when {
            "sitting" in t -> tokens += "sitting"
            "standing" in t -> tokens += "standing"
            "walking" in t -> tokens += "walking"
            "leaning" in t -> tokens += "leaning"
        }
        when {
            "cinematic" in t || "moody" in t -> tokens += "cinematic moody"
            "fashion" in t -> tokens += "fashion editorial"
            "studio" in t -> tokens += "studio"
            "street" in t -> tokens += "street"
            "beach" in t -> tokens += "beach"
            "coffee" in t || "cafe" in t -> tokens += "coffee shop window light"
            "mirror" in t -> tokens += "mirror"
            "car" in t -> tokens += "car night"
            "night" in t -> tokens += "night urban"
            "golden hour" in t || "sunset" in t -> tokens += "golden hour"
        }
        if ("background" in t) tokens += "background ideas"
        if ("style" in t) tokens += "editorial style"
        if (tokens.isEmpty()) tokens += "full body"

        return (tokens + listOf("portrait pose", "photography", "reference")).joinToString(" ")
    }

    private fun startFakeCoachLoop() {
        fakeCoachJob?.cancel()
        fakeCoachJob = viewModelScope.launch {
            val script = listOf(
                "Tilt the camera up a little",
                "Tilt the camera down a bit",
                "Move slightly to the left",
                "Pan a touch to the right",
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

    private companion object {
        const val TAG = "Vantage"

        // Lowercase phrases that trigger the keyword-fallback inspiration path.
        // Matched via `contains` against the lowercased transcript.
        val INSPIRATION_PHRASES = listOf(
            "find me poses",
            "show me poses",
            "pose ideas",
            "pose reference",
            "find inspiration",
            "show inspiration",
            "find me inspiration",
            "give me inspiration",
            "show me inspiration",
            "give me ideas",
            "reference photos",
            "how should i pose",
            "what pose"
        )
    }
}
