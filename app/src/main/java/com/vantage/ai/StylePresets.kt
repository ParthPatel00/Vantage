package com.vantage.ai

import com.vantage.models.FilterType

object StylePresets {

    data class Preset(
        val brightness: Float,
        val contrast: Float,
        val saturation: Float,
        val gamma: Float
    )

    val presets: Map<FilterType, Preset> = mapOf(
        FilterType.CINEMATIC to Preset(-0.05f, 1.35f, 0.85f, 1.15f),
        FilterType.VINTAGE to Preset(0.05f, 1.15f, 0.75f, 1.20f),
        FilterType.DRAMATIC to Preset(-0.08f, 1.50f, 0.80f, 1.10f),
        FilterType.NOIR to Preset(-0.05f, 1.60f, 0.0f, 1.05f),
        FilterType.VIVID to Preset(0.02f, 1.20f, 1.45f, 1.05f),
        FilterType.WARM to Preset(0.03f, 1.15f, 1.15f, 1.10f),
        FilterType.COOL to Preset(0.0f, 1.15f, 1.05f, 1.05f),
        FilterType.MUTED to Preset(0.05f, 0.90f, 0.60f, 1.12f),
        FilterType.FADE to Preset(0.08f, 0.85f, 0.80f, 1.15f),
        FilterType.MONO to Preset(0.0f, 1.30f, 0.0f, 1.05f),
        FilterType.SILVERTONE to Preset(0.0f, 1.20f, 0.10f, 1.08f),
    )

    val defaultEnhancement = Preset(0.08f, 1.30f, 1.25f, 1.12f)
}
