package com.vantage.models

data class CameraSettings(
    val zoom: Float? = null,
    val iso: Int? = null,
    val shutterSpeed: Int? = null,
    val whiteBalance: String? = null,
    val focusDistance: Float? = null,
    val exposureComp: Float? = null
)
