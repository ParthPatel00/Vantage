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
        // Original filters
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

        // Film stocks
        FilterType.KODAK_GOLD to Preset(0.04f, 1.20f, 1.15f, 1.08f),
        FilterType.FUJI_VELVIA to Preset(0.0f, 1.35f, 1.45f, 1.0f),
        FilterType.PORTRA to Preset(0.05f, 0.95f, 0.85f, 1.12f),
        FilterType.EKTACHROME to Preset(0.0f, 1.25f, 1.10f, 1.0f),
        FilterType.TRI_X to Preset(-0.03f, 1.50f, 0.0f, 1.0f),
        FilterType.SUPERIA to Preset(0.02f, 1.15f, 1.05f, 1.05f),
        FilterType.PROVIA to Preset(0.0f, 1.18f, 1.08f, 1.0f),

        // Cinematic
        FilterType.TEAL_ORANGE to Preset(-0.03f, 1.40f, 0.85f, 1.10f),
        FilterType.BLADE_RUNNER to Preset(-0.08f, 1.45f, 0.90f, 1.15f),
        FilterType.MATRIX to Preset(-0.03f, 1.25f, 0.90f, 1.05f),
        FilterType.MOONLIGHT to Preset(-0.05f, 1.20f, 0.65f, 1.10f),

        // Mood
        FilterType.GOLDEN_HOUR to Preset(0.06f, 1.10f, 1.20f, 1.08f),
        FilterType.BLUE_HOUR to Preset(-0.03f, 1.20f, 0.88f, 1.05f),
        FilterType.NEON_NIGHT to Preset(-0.05f, 1.40f, 1.55f, 1.10f),
        FilterType.ARCTIC to Preset(0.0f, 1.25f, 0.40f, 1.05f),
        FilterType.MISTY to Preset(0.06f, 0.85f, 0.72f, 1.15f),

        // Retro
        FilterType.POLAROID to Preset(0.05f, 0.95f, 0.85f, 1.10f),
        FilterType.SEVENTIES to Preset(0.04f, 1.05f, 0.70f, 1.18f),
        FilterType.EIGHTIES to Preset(0.0f, 1.25f, 1.35f, 1.05f),
        FilterType.VHS to Preset(0.04f, 0.90f, 0.80f, 1.12f),

        // Creative
        FilterType.POP_ART to Preset(0.0f, 1.50f, 1.80f, 1.0f),
        FilterType.CROSS_PROCESS to Preset(0.0f, 1.30f, 1.20f, 1.05f),
        FilterType.LOMO to Preset(0.0f, 1.40f, 1.40f, 1.05f),
        FilterType.INFRARED to Preset(0.0f, 1.20f, 1.15f, 1.05f),
        FilterType.CANDY to Preset(0.08f, 0.90f, 1.25f, 1.12f),

        // Portrait
        FilterType.PORCELAIN to Preset(0.03f, 0.95f, 0.88f, 1.08f),
        FilterType.PEACH to Preset(0.04f, 1.0f, 0.95f, 1.05f),
        FilterType.GLOW to Preset(0.05f, 0.92f, 1.05f, 1.10f),

        // Professional
        FilterType.MAGAZINE to Preset(0.0f, 1.40f, 1.15f, 1.0f),
        FilterType.WEDDING to Preset(0.04f, 0.95f, 0.88f, 1.08f),
        FilterType.FOOD to Preset(0.04f, 1.18f, 1.25f, 1.05f),
        FilterType.CLEAN_EDIT to Preset(0.03f, 1.15f, 1.08f, 1.03f),

        // Fun/Demo
        FilterType.CARTOON to Preset(0.02f, 1.50f, 1.60f, 1.0f),
        FilterType.ANIME to Preset(0.02f, 1.40f, 1.50f, 1.0f),
        FilterType.SKETCH to Preset(0.0f, 1.60f, 0.0f, 1.0f),
        FilterType.COMIC_BOOK to Preset(0.0f, 1.55f, 1.70f, 1.0f),
        FilterType.OIL_PAINTING to Preset(0.03f, 1.20f, 1.35f, 1.05f),
        FilterType.CYBERPUNK to Preset(-0.06f, 1.45f, 1.30f, 1.15f),
        FilterType.UNDERWATER to Preset(-0.04f, 1.15f, 0.80f, 1.08f),
        FilterType.MARS to Preset(0.02f, 1.25f, 1.10f, 1.05f),
        FilterType.AURORA to Preset(0.0f, 1.25f, 1.40f, 1.05f),
        FilterType.RADIOACTIVE to Preset(-0.03f, 1.45f, 1.30f, 1.10f),
    )

    val defaultEnhancement = Preset(0.08f, 1.30f, 1.25f, 1.12f)
}
