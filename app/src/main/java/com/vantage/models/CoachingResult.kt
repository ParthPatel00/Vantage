package com.vantage.models

data class CoachingResult(
    val sceneDescription: String = "",
    val cameraSettings: CameraSettings = CameraSettings(),
    val filter: FilterType = FilterType.NATURAL,
    val userActions: List<String> = emptyList(),
    val voiceMessage: String = "",
    val unsplashQuery: String = "",
    val readyToCapture: Boolean = false
)
