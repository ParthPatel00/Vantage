package com.vantage.models

import android.net.Uri
import com.vantage.camera.standard.AspectRatioManager

data class CameraUiState(
    val flashMode: FlashMode = FlashMode.OFF,
    val isFrontCamera: Boolean = false,
    val currentRatio: AspectRatioManager.AspectRatio = AspectRatioManager.AspectRatio.RATIO_4_3,
    val currentZoom: Float = 1f,
    val currentFilter: FilterType = FilterType.NATURAL,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val gamma: Float = 1f,
    val isAiActive: Boolean = false,
    val analysisIteration: Int = 0,
    val aiReasoning: String = "",
    val lastCapturedUri: Uri? = null,
    val isListening: Boolean = false,
    val voicePrompt: String = "",
    val subjectBox: List<Int> = emptyList(),
    val suggestedBox: List<Int> = emptyList(),
    val compositionTip: String = "",
    val compositionOk: Boolean = true,
    val sceneDescription: String = "",
    val photographyTip: String = "",
    val aiMessages: List<String> = emptyList(),
    val inspoPhotos: List<UnsplashPhoto> = emptyList(),
    val selectedInspoPhoto: UnsplashPhoto? = null
)
