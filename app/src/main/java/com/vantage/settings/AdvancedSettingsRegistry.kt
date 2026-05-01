package com.vantage.settings

import android.hardware.camera2.CameraMetadata

/**
 * Registry of all advanced settings available in the Pro camera.
 */
object AdvancedSettingsRegistry {

    enum class SettingType {
        NONE,
        BRIGHTNESS,
        CONTRAST,
        SATURATION,
        GAMMA,
        EV, // Exposure Compensation (-3 to +3)
        ISO,
        SHUTTER,
        FOCUS,
        WB,
        SHARPNESS,
        NOISE_REDUCTION
    }

    data class SettingDefinition(
        val type: SettingType,
        val label: String,
        val range: ClosedFloatingPointRange<Float>,
        val defaultValue: Float,
        val isOpenGL: Boolean = false // True if handled by shader, false if Camera2
    )

    val ALL_SETTINGS = listOf(
        // OpenGL Adjustments
        SettingDefinition(SettingType.BRIGHTNESS, "Brightness", -2.0f..2.0f, 0.0f, true),
        SettingDefinition(SettingType.CONTRAST, "Contrast", 0.0f..4.0f, 1.0f, true),
        SettingDefinition(SettingType.SATURATION, "Saturation", 0.0f..4.0f, 1.0f, true),
        SettingDefinition(SettingType.GAMMA, "Gamma", 0.1f..5.0f, 1.0f, true),
        
        // Camera2 Manual Controls
        SettingDefinition(SettingType.EV, "Exposure (EV)", -3.0f..3.0f, 0.0f),
        SettingDefinition(SettingType.ISO, "ISO", 10f..6400f, 100f),
        SettingDefinition(SettingType.SHUTTER, "Shutter (1/x)", 1f..8000f, 60f), 
        SettingDefinition(SettingType.FOCUS, "Focus", 0f..20f, 0f), 
        
        // Camera2 Enums (Mapped to float for slider)
        SettingDefinition(SettingType.WB, "White Balance", 1f..8f, 1f), 
        SettingDefinition(SettingType.SHARPNESS, "Sharpness", 0f..3f, 0f),
        SettingDefinition(SettingType.NOISE_REDUCTION, "Denoise", 0f..4f, 0f)
    )
}
