package com.vantage.models

data class EnhancementInfo(
    val filter: FilterType = FilterType.NATURAL,
    val iso: Int = 200,
    val shutter: Int = 125,
    val whiteBalance: String = "auto",
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val gamma: Float = 1f,
    val zoom: Float = 1f,
    val sceneDescription: String = "",
    val aiReasoning: String = "",
    val voicePrompt: String = "",
    val photographyTip: String = "",
    val referenceImageUrl: String = ""
)
