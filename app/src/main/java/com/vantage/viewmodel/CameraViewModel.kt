package com.vantage.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vantage.ai.CameraToolSet
import com.vantage.ai.GemmaEngine
import com.vantage.ai.SceneAnalysis
import com.vantage.ai.ToolResult
import com.vantage.api.UnsplashClientImpl
import com.vantage.camera.standard.AspectRatioManager
import com.vantage.contracts.IUnsplashClient
import com.vantage.models.CameraUiState
import com.vantage.models.EnhancementInfo
import com.vantage.models.FilterType
import com.vantage.models.FlashMode
import com.vantage.models.UnsplashPhoto
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val gemmaEngine = GemmaEngine()
    private val unsplashClient: IUnsplashClient = UnsplashClientImpl()
    private val cameraToolSet = CameraToolSet(unsplashClient)

    // The screen collects [snapshotRequests] and replies via [onInspoSnapshotCaptured]
    // when a fresh preview frame is on disk. The inspiration flow uses this so it has
    // scene context for the Gemma-generated Unsplash query without needing a manual capture.
    private val _snapshotRequests = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val snapshotRequests: SharedFlow<Long> = _snapshotRequests.asSharedFlow()
    private val _snapshotResults = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private var lastInspoFramePath: String? = null

    private var pendingReferenceJob: Deferred<Pair<String, String>?>? = null

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

    private val metadataFile = File(application.filesDir, "enhancement_metadata.json")
    private val _enhancementMetadata = MutableStateFlow(loadMetadata())
    val enhancementMetadata: StateFlow<Map<Long, EnhancementInfo>> = _enhancementMetadata.asStateFlow()

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
            delay(15_000)
            if (isLoopActive) {
                Log.w("Vantage", "AI analysis timed out after 15s, shooting with defaults")
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
                val referenceResult = pendingReferenceJob?.let { job ->
                    withTimeoutOrNull(6000L) { job.await() }
                }
                pendingReferenceJob = null
                val referencePath = referenceResult?.first
                val referenceImageUrl = referenceResult?.second ?: ""
                if (referencePath != null) Log.d("Vantage", "Reference image ready: $referencePath")
                val userIntent = _uiState.value.voicePrompt.ifBlank { null }
                val analysis = if (gemmaEngine.isReady()) {
                    gemmaEngine.analyzeScene(
                        framePath, analysisIteration, lastAnalysis, userIntent,
                        referenceImagePath = if (analysisIteration == 0) referencePath else null
                    )
                } else {
                    Log.w("Vantage", "Gemma not ready, using defaults")
                    SceneAnalysis(ready = true, reasoning = "AI warming up")
                }

                if (!isLoopActive) {
                    Log.d("Vantage", "Loop cancelled during analysis, discarding result")
                    return@launch
                }

                Log.d("Vantage", "Gemma result: ready=${analysis.ready} filter=${analysis.filter} reason=${analysis.reasoning}")

                // Voice prompt override: if user explicitly asked for a style, force that filter
                val intentFilter = if (userIntent != null) {
                    val matched = FilterType.fromString(userIntent)
                    if (matched != FilterType.NATURAL) matched else null
                } else null

                val correctedAnalysis = if (intentFilter != null && analysis.filter != intentFilter) {
                    Log.d("Vantage", "Voice intent override: ${analysis.filter} -> $intentFilter (from '$userIntent')")
                    lockedFilter = intentFilter
                    analysis.copy(filter = intentFilter)
                } else if (analysisIteration == 0) {
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

                val ts = _captureTimestamp.value
                val voicePrompt = _uiState.value.voicePrompt
                _enhancementMetadata.update { map ->
                    map + (ts to EnhancementInfo(
                        filter = correctedAnalysis.filter,
                        iso = correctedAnalysis.iso,
                        shutter = correctedAnalysis.shutter,
                        whiteBalance = wbModeToLabel(correctedAnalysis.wbMode),
                        brightness = correctedAnalysis.brightness,
                        contrast = correctedAnalysis.contrast,
                        saturation = correctedAnalysis.saturation,
                        gamma = correctedAnalysis.gamma,
                        zoom = correctedAnalysis.zoom,
                        sceneDescription = correctedAnalysis.sceneDescription,
                        aiReasoning = correctedAnalysis.reasoning,
                        voicePrompt = voicePrompt,
                        photographyTip = correctedAnalysis.photographyTip,
                        referenceImageUrl = referenceImageUrl
                    ))
                }
                saveMetadata()

                _uiState.update { it.copy(isAiActive = false) }
                referencePath?.let { File(it).delete() }
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
        Log.d("Vantage", "Zoom selected: $zoom")
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
        if (!isInspirationIntent(text)) {
            precomputeReferenceImage(text)
        } else {
            maybeRouteVoiceToInspiration(text)
        }
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

    // ─── Inspiration tool ────────────────────────────────────────────────────────────

    fun onInspoSnapshotCaptured(framePath: String) {
        Log.d(TAG, "Inspo snapshot captured: $framePath")
        lastInspoFramePath = framePath
        _snapshotResults.tryEmit(framePath)
    }

    fun onInspoCardDismissed() {
        _uiState.update { it.copy(inspoPhotos = emptyList(), selectedInspoPhoto = null) }
    }

    fun onInspoPhotoSelected(photo: UnsplashPhoto) {
        _uiState.update { it.copy(selectedInspoPhoto = photo) }
        val refDesc = photo.altDescription.ifBlank { "selected Unsplash inspiration photo" }
        val hint = "Match this style: $refDesc"
        _uiState.update {
            it.copy(
                voicePrompt = hint,
                aiMessages = it.aiMessages + "Reference locked. Tap the AI shutter to recreate it."
            )
        }
        Log.d(TAG, "Inspo selected: $refDesc -> voicePrompt='$hint'")
    }

    fun triggerPoseInspiration() {
        viewModelScope.launch { launchInspirationFlow("find me poses to do") }
    }

    private fun maybeRouteVoiceToInspiration(text: String) {
        val normalized = text.lowercase().trim()
        val matchesPhrase = INSPIRATION_PHRASES.any { normalized.contains(it) }
        val matchesRoot = INSPIRATION_ROOTS.any { normalized.contains(it) }
        if (matchesPhrase || matchesRoot) {
            Log.d(TAG, "Inspiration intent detected (phrase=$matchesPhrase root=$matchesRoot)")
            viewModelScope.launch { launchInspirationFlow(text) }
        }
    }

    private suspend fun launchInspirationFlow(transcript: String) {
        val framePath = captureAndAwaitFrame(timeoutMs = 1500L)
        val gemmaQuery = framePath?.let { generateInspoQueryFromGemma(transcript, it) }
        val query = gemmaQuery ?: buildPoseQuery(transcript)
        val sceneTag = if (gemmaQuery != null) "gemma" else "fallback"
        Log.d(TAG, "Inspiration query [$sceneTag]: '$query'")

        val voiceMessage =
            "I'll pull some references that fit this scene. Tap one and I'll help you recreate it."
        val result = cameraToolSet.findInspirationPhotos(
            userRequest = transcript,
            sceneDescription = framePath ?: "",
            inspirationType = "human_pose",
            unsplashQuery = query,
            voiceMessage = voiceMessage
        )
        applyInspirationToolResult(result)
    }

    private fun applyInspirationToolResult(result: ToolResult.Inspiration) {
        _uiState.update {
            it.copy(
                inspoPhotos = result.photos,
                selectedInspoPhoto = null,
                aiMessages = it.aiMessages + result.voiceMessage
            )
        }
    }

    private suspend fun captureAndAwaitFrame(timeoutMs: Long): String? {
        _snapshotRequests.tryEmit(System.currentTimeMillis())
        return withTimeoutOrNull(timeoutMs) { _snapshotResults.first() }
    }

    private suspend fun generateInspoQueryFromGemma(transcript: String, framePath: String): String? {
        if (!gemmaEngine.isReady()) return null
        val prompt = """
            The user said: "$transcript"
            Look at the camera scene and produce a CONCISE Unsplash search query (5-10
            words) for inspiration photos that match the request, the people in frame,
            the setting, lighting, and mood. For pose requests, prioritize human pose
            references.
            Output ONLY the query as plain text. No quotes, no labels, no explanation.
        """.trimIndent()
        return try {
            val raw = gemmaEngine.queryWithImage(framePath, prompt)
            if (!isUsableModelResponse(raw)) null else sanitizeUnsplashQuery(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Gemma query generation failed: ${e.message}")
            null
        }
    }

    private fun isUsableModelResponse(text: String): Boolean =
        text.isNotBlank() &&
            !text.startsWith("Error:") &&
            text != "Engine not ready" &&
            text != "No response"

    private fun sanitizeUnsplashQuery(raw: String): String? {
        val first = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: return null
        val cleaned = first
            .removePrefix("\"").removeSuffix("\"")
            .removePrefix("'").removeSuffix("'")
            .trimEnd('.', ',', ';', ':')
            .take(80)
        return cleaned.takeIf { it.length >= 3 }
    }

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

    private fun precomputeReferenceImage(voiceText: String) {
        pendingReferenceJob?.cancel()
        pendingReferenceJob = viewModelScope.async {
            val framePath = captureAndAwaitFrame(timeoutMs = 1500L)
            val sceneTag = if (framePath != null && gemmaEngine.isReady()) {
                try {
                    val raw = gemmaEngine.queryWithImage(
                        framePath,
                        "Describe this photo in 1-4 words. Subject and setting only. Example: woman portrait outdoor, cat sleeping sofa, city skyline night. Output ONLY the words."
                    )
                    if (isUsableModelResponse(raw)) {
                        raw.lines().first().trim().take(40)
                    } else null
                } catch (e: Exception) {
                    Log.w(TAG, "Scene tag failed: ${e.message}")
                    null
                }
            } else null

            if (sceneTag != null) Log.d(TAG, "Scene tag: '$sceneTag'")
            fetchReferenceImage(voiceText, sceneTag)
        }
    }

    private suspend fun fetchReferenceImage(query: String, sceneTag: String? = null): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            try {
                val searchQuery = buildString {
                    append(query)
                    if (sceneTag != null) append(" $sceneTag")
                    append(" photography aesthetic")
                }
                Log.d(TAG, "Reference search: '$searchQuery'")
                val photo = unsplashClient.searchPhotos(searchQuery, perPage = 1)
                    .getOrNull()?.firstOrNull() ?: return@withContext null
                val tempFile = File(
                    getApplication<Application>().cacheDir,
                    "ref_${System.currentTimeMillis()}.jpg"
                )
                val success = com.vantage.api.UnsplashApiService().downloadImage(photo.smallUrl, tempFile)
                if (success) {
                    Log.d(TAG, "Reference image downloaded: ${tempFile.length()} bytes")
                    Pair(tempFile.absolutePath, photo.smallUrl)
                } else {
                    tempFile.delete()
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reference image fetch failed: ${e.message}")
                null
            }
        }

    private fun loadMetadata(): Map<Long, EnhancementInfo> {
        if (!metadataFile.exists()) return emptyMap()
        return try {
            val root = JSONObject(metadataFile.readText())
            val map = mutableMapOf<Long, EnhancementInfo>()
            root.keys().forEach { key ->
                val ts = key.toLongOrNull() ?: return@forEach
                val j = root.getJSONObject(key)
                map[ts] = EnhancementInfo(
                    filter = FilterType.fromString(j.optString("filter", "NATURAL")),
                    iso = j.optInt("iso", 200),
                    shutter = j.optInt("shutter", 125),
                    whiteBalance = j.optString("whiteBalance", "auto"),
                    brightness = j.optDouble("brightness", 0.0).toFloat(),
                    contrast = j.optDouble("contrast", 1.0).toFloat(),
                    saturation = j.optDouble("saturation", 1.0).toFloat(),
                    gamma = j.optDouble("gamma", 1.0).toFloat(),
                    zoom = j.optDouble("zoom", 1.0).toFloat(),
                    sceneDescription = j.optString("sceneDescription", ""),
                    aiReasoning = j.optString("aiReasoning", ""),
                    voicePrompt = j.optString("voicePrompt", ""),
                    photographyTip = j.optString("photographyTip", ""),
                    referenceImageUrl = j.optString("referenceImageUrl", "")
                )
            }
            map
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load metadata: ${e.message}")
            emptyMap()
        }
    }

    private fun saveMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = JSONObject()
                _enhancementMetadata.value.forEach { (ts, info) ->
                    val j = JSONObject().apply {
                        put("filter", info.filter.name)
                        put("iso", info.iso)
                        put("shutter", info.shutter)
                        put("whiteBalance", info.whiteBalance)
                        put("brightness", info.brightness.toDouble())
                        put("contrast", info.contrast.toDouble())
                        put("saturation", info.saturation.toDouble())
                        put("gamma", info.gamma.toDouble())
                        put("zoom", info.zoom.toDouble())
                        put("sceneDescription", info.sceneDescription)
                        put("aiReasoning", info.aiReasoning)
                        put("voicePrompt", info.voicePrompt)
                        put("photographyTip", info.photographyTip)
                        put("referenceImageUrl", info.referenceImageUrl)
                    }
                    root.put(ts.toString(), j)
                }
                metadataFile.writeText(root.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save metadata: ${e.message}")
            }
        }
    }

    private fun isInspirationIntent(text: String): Boolean {
        val n = text.lowercase().trim()
        return INSPIRATION_PHRASES.any { n.contains(it) } || INSPIRATION_ROOTS.any { n.contains(it) }
    }

    private fun wbModeToLabel(mode: Int): String = when (mode) {
        android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT -> "Incandescent"
        android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT -> "Fluorescent"
        android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT -> "Daylight"
        android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "Cloudy"
        android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_TWILIGHT -> "Twilight"
        android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_SHADE -> "Shade"
        else -> "Auto"
    }

    private companion object {
        const val TAG = "Vantage"
        val INSPIRATION_PHRASES = listOf(
            "find me poses", "show me poses", "give me poses",
            "pose ideas", "pose reference",
            "find inspiration", "show inspiration",
            "find me inspiration", "give me inspiration", "show me inspiration",
            "give me ideas", "reference photos",
            "how should i pose", "what pose"
        )
        val INSPIRATION_ROOTS = listOf("pose", "posing", "inspir", "reference photo")
    }
}
