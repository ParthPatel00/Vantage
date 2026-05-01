package com.vantage.ai

import android.hardware.camera2.CameraMetadata
import com.vantage.models.FilterType

data class SceneAnalysis(
    val filter: FilterType = FilterType.NATURAL,
    val iso: Int = 200,
    val shutter: Int = 125,
    val wbMode: Int = CameraMetadata.CONTROL_AWB_MODE_AUTO,
    val focusDistance: Float = 0f,
    val noiseReductionMode: Int = CameraMetadata.NOISE_REDUCTION_MODE_FAST,
    val sharpnessMode: Int = CameraMetadata.EDGE_MODE_FAST,
    val zoom: Float = 1f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val gamma: Float = 1f,
    val flash: String = "off",
    val ready: Boolean = false,
    val reasoning: String = "",
    val rawResponse: String = "",
    val subjectBox: List<Int> = emptyList(),
    val suggestedBox: List<Int> = emptyList(),
    val compositionTip: String = "",
    val compositionOk: Boolean = true,
    val sceneDescription: String = "",
    val photographyTip: String = ""
)
