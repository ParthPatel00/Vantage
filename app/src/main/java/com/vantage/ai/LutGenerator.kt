package com.vantage.ai

import android.graphics.Bitmap
import android.graphics.Color
import com.vantage.models.FilterType
import kotlin.math.pow
import kotlin.math.sin

object LutGenerator {

    private val cache = HashMap<FilterType, Bitmap>()

    fun get(type: FilterType): Bitmap = cache.getOrPut(type) { generate(type) }

    private fun generate(type: FilterType): Bitmap {
        val pixels = IntArray(512 * 512)
        for (y in 0 until 512) {
            for (x in 0 until 512) {
                val r = (x % 64) / 63f
                val g = (y % 64) / 63f
                val b = ((y / 64) * 8 + (x / 64)) / 63f
                val rgb = floatArrayOf(r, g, b)
                applyRecipe(type, rgb)
                clamp(rgb)
                pixels[y * 512 + x] = Color.argb(
                    255,
                    (rgb[0] * 255f + 0.5f).toInt().coerceIn(0, 255),
                    (rgb[1] * 255f + 0.5f).toInt().coerceIn(0, 255),
                    (rgb[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
                )
            }
        }
        return Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888)
    }

    private fun applyRecipe(type: FilterType, rgb: FloatArray) {
        when (type) {
            FilterType.KODAK_GOLD -> {
                warmth(rgb, 0.12f)
                satAdjust(rgb, 1.15f)
                sCurve(rgb, 0.25f)
                liftBlacks(rgb, 0.03f)
                channelBoost(rgb, 0, 0.04f)
                splitTone(rgb, fa(0.10f, 0.06f, 0.0f), fa(1.0f, 0.95f, 0.80f), 0.25f)
            }
            FilterType.FUJI_VELVIA -> {
                satAdjust(rgb, 1.45f)
                sCurve(rgb, 0.45f)
                coolShift(rgb, 0.04f)
                channelBoost(rgb, 1, 0.03f)
                channelBoost(rgb, 2, 0.05f)
            }
            FilterType.PORTRA -> {
                warmth(rgb, 0.06f)
                satAdjust(rgb, 0.82f)
                sCurve(rgb, -0.15f)
                liftBlacks(rgb, 0.05f)
                splitTone(rgb, fa(0.08f, 0.05f, 0.04f), fa(1.0f, 0.97f, 0.92f), 0.20f)
            }
            FilterType.EKTACHROME -> {
                coolShift(rgb, 0.08f)
                satAdjust(rgb, 1.12f)
                sCurve(rgb, 0.30f)
                splitTone(rgb, fa(0.0f, 0.04f, 0.10f), fa(1.0f, 0.98f, 0.95f), 0.15f)
            }
            FilterType.TRI_X -> {
                desaturate(rgb)
                sCurve(rgb, 0.50f)
                warmth(rgb, 0.03f)
            }
            FilterType.SUPERIA -> {
                splitTone(rgb, fa(0.02f, 0.08f, 0.03f), fa(1.0f, 0.95f, 0.85f), 0.30f)
                satAdjust(rgb, 1.05f)
                sCurve(rgb, 0.20f)
                liftBlacks(rgb, 0.02f)
            }
            FilterType.PROVIA -> {
                coolShift(rgb, 0.03f)
                satAdjust(rgb, 1.08f)
                sCurve(rgb, 0.22f)
            }
            FilterType.TEAL_ORANGE -> {
                splitTone(rgb, fa(0.0f, 0.12f, 0.15f), fa(1.0f, 0.85f, 0.65f), 0.45f)
                satAdjust(rgb, 0.85f)
                sCurve(rgb, 0.40f)
                liftBlacks(rgb, 0.02f)
            }
            FilterType.BLADE_RUNNER -> {
                splitTone(rgb, fa(0.0f, 0.08f, 0.18f), fa(0.90f, 0.70f, 0.85f), 0.50f)
                satAdjust(rgb, 0.90f)
                sCurve(rgb, 0.45f)
                gamma(rgb, 1.15f)
                channelBoost(rgb, 2, 0.06f)
            }
            FilterType.MATRIX -> {
                channelBoost(rgb, 1, 0.10f)
                channelDim(rgb, 0, 0.06f)
                channelDim(rgb, 2, 0.08f)
                sCurve(rgb, 0.25f)
                satAdjust(rgb, 0.90f)
                liftBlacks(rgb, 0.02f)
            }
            FilterType.MOONLIGHT -> {
                coolShift(rgb, 0.15f)
                satAdjust(rgb, 0.65f)
                sCurve(rgb, 0.20f)
                channelBoost(rgb, 2, 0.12f)
                gamma(rgb, 1.10f)
                liftBlacks(rgb, 0.04f)
            }
            FilterType.GOLDEN_HOUR -> {
                warmth(rgb, 0.18f)
                satAdjust(rgb, 1.20f)
                sCurve(rgb, -0.10f)
                splitTone(rgb, fa(0.05f, 0.03f, 0.0f), fa(1.0f, 0.92f, 0.70f), 0.30f)
                channelBoost(rgb, 0, 0.06f)
            }
            FilterType.BLUE_HOUR -> {
                coolShift(rgb, 0.14f)
                satAdjust(rgb, 0.88f)
                sCurve(rgb, 0.22f)
                channelBoost(rgb, 2, 0.10f)
                splitTone(rgb, fa(0.02f, 0.03f, 0.12f), fa(0.95f, 0.90f, 1.0f), 0.25f)
            }
            FilterType.NEON_NIGHT -> {
                satAdjust(rgb, 1.55f)
                sCurve(rgb, 0.50f)
                channelBoost(rgb, 0, 0.05f)
                channelBoost(rgb, 2, 0.08f)
                gamma(rgb, 1.10f)
            }
            FilterType.ARCTIC -> {
                coolShift(rgb, 0.18f)
                satAdjust(rgb, 0.40f)
                sCurve(rgb, 0.30f)
                liftBlacks(rgb, 0.06f)
                channelBoost(rgb, 2, 0.08f)
            }
            FilterType.MISTY -> {
                liftBlacks(rgb, 0.10f)
                crushHighlights(rgb, 0.06f)
                satAdjust(rgb, 0.72f)
                coolShift(rgb, 0.05f)
                sCurve(rgb, -0.20f)
            }
            FilterType.POLAROID -> {
                warmth(rgb, 0.08f)
                liftBlacks(rgb, 0.06f)
                satAdjust(rgb, 0.85f)
                sCurve(rgb, -0.10f)
                splitTone(rgb, fa(0.0f, 0.04f, 0.06f), fa(1.0f, 0.98f, 0.90f), 0.15f)
            }
            FilterType.SEVENTIES -> {
                warmth(rgb, 0.16f)
                satAdjust(rgb, 0.70f)
                liftBlacks(rgb, 0.08f)
                crushHighlights(rgb, 0.05f)
                splitTone(rgb, fa(0.10f, 0.06f, 0.0f), fa(0.95f, 0.88f, 0.70f), 0.35f)
            }
            FilterType.EIGHTIES -> {
                channelBoost(rgb, 0, 0.08f)
                channelBoost(rgb, 2, 0.10f)
                satAdjust(rgb, 1.35f)
                sCurve(rgb, 0.30f)
                splitTone(rgb, fa(0.06f, 0.0f, 0.10f), fa(1.0f, 0.80f, 0.90f), 0.25f)
            }
            FilterType.VHS -> {
                warmth(rgb, 0.10f)
                satAdjust(rgb, 0.80f)
                liftBlacks(rgb, 0.05f)
                sCurve(rgb, -0.15f)
                channelBoost(rgb, 0, 0.05f)
                crushHighlights(rgb, 0.03f)
            }
            FilterType.POP_ART -> {
                satAdjust(rgb, 1.80f)
                sCurve(rgb, 0.55f)
                channelBoost(rgb, 0, 0.04f)
                channelBoost(rgb, 1, 0.02f)
            }
            FilterType.CROSS_PROCESS -> {
                val lum = luminance(rgb)
                if (lum < 0.5f) {
                    channelBoost(rgb, 1, 0.12f * (1f - lum * 2f))
                    channelDim(rgb, 0, 0.06f * (1f - lum * 2f))
                } else {
                    channelBoost(rgb, 0, 0.10f * (lum * 2f - 1f))
                    channelDim(rgb, 2, 0.08f * (lum * 2f - 1f))
                }
                satAdjust(rgb, 1.20f)
                sCurve(rgb, 0.35f)
            }
            FilterType.LOMO -> {
                satAdjust(rgb, 1.40f)
                sCurve(rgb, 0.45f)
                warmth(rgb, 0.06f)
                liftBlacks(rgb, 0.02f)
                channelBoost(rgb, 0, 0.03f)
            }
            FilterType.INFRARED -> {
                val origR = rgb[0]; val origG = rgb[1]; val origB = rgb[2]
                rgb[0] = (origG * 0.6f + origR * 0.4f + 0.10f).coerceAtMost(1f)
                rgb[1] = (origR * 0.3f + origG * 0.3f + origB * 0.1f)
                rgb[2] = (origB * 0.5f + origG * 0.2f)
                satAdjust(rgb, 1.15f)
                sCurve(rgb, 0.25f)
            }
            FilterType.CANDY -> {
                liftBlacks(rgb, 0.12f)
                crushHighlights(rgb, 0.03f)
                satAdjust(rgb, 1.25f)
                sCurve(rgb, -0.20f)
                warmth(rgb, 0.05f)
                channelBoost(rgb, 0, 0.04f)
                channelBoost(rgb, 2, 0.03f)
            }
            FilterType.PORCELAIN -> {
                satAdjust(rgb, 0.88f)
                sCurve(rgb, -0.15f)
                coolShift(rgb, 0.03f)
                liftBlacks(rgb, 0.04f)
                splitTone(rgb, fa(0.02f, 0.02f, 0.04f), fa(1.0f, 0.98f, 0.96f), 0.10f)
            }
            FilterType.PEACH -> {
                warmth(rgb, 0.10f)
                satAdjust(rgb, 0.95f)
                sCurve(rgb, -0.10f)
                splitTone(rgb, fa(0.04f, 0.02f, 0.0f), fa(1.0f, 0.94f, 0.88f), 0.20f)
                channelBoost(rgb, 0, 0.03f)
            }
            FilterType.GLOW -> {
                warmth(rgb, 0.08f)
                satAdjust(rgb, 1.05f)
                sCurve(rgb, -0.18f)
                liftBlacks(rgb, 0.04f)
                splitTone(rgb, fa(0.02f, 0.01f, 0.0f), fa(1.0f, 0.97f, 0.90f), 0.15f)
            }
            FilterType.MAGAZINE -> {
                sCurve(rgb, 0.45f)
                satAdjust(rgb, 1.15f)
                coolShift(rgb, 0.03f)
                channelBoost(rgb, 2, 0.02f)
            }
            FilterType.WEDDING -> {
                warmth(rgb, 0.06f)
                satAdjust(rgb, 0.88f)
                sCurve(rgb, -0.12f)
                liftBlacks(rgb, 0.03f)
                splitTone(rgb, fa(0.02f, 0.01f, 0.02f), fa(1.0f, 0.97f, 0.95f), 0.12f)
            }
            FilterType.FOOD -> {
                warmth(rgb, 0.12f)
                satAdjust(rgb, 1.25f)
                sCurve(rgb, 0.22f)
                channelBoost(rgb, 0, 0.04f)
                channelBoost(rgb, 1, 0.02f)
            }
            FilterType.CLEAN_EDIT -> {
                sCurve(rgb, 0.18f)
                liftBlacks(rgb, 0.02f)
                satAdjust(rgb, 1.08f)
                warmth(rgb, 0.02f)
            }
            else -> { }
        }
    }

    // --- Color transformation helpers ---

    private fun fa(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

    private fun clamp(rgb: FloatArray) {
        rgb[0] = rgb[0].coerceIn(0f, 1f)
        rgb[1] = rgb[1].coerceIn(0f, 1f)
        rgb[2] = rgb[2].coerceIn(0f, 1f)
    }

    private fun luminance(rgb: FloatArray): Float =
        0.299f * rgb[0] + 0.587f * rgb[1] + 0.114f * rgb[2]

    private fun warmth(rgb: FloatArray, amount: Float) {
        rgb[0] = rgb[0] + amount * 0.8f
        rgb[1] = rgb[1] + amount * 0.2f
        rgb[2] = rgb[2] - amount * 0.6f
    }

    private fun coolShift(rgb: FloatArray, amount: Float) {
        rgb[0] = rgb[0] - amount * 0.5f
        rgb[1] = rgb[1] + amount * 0.1f
        rgb[2] = rgb[2] + amount * 0.8f
    }

    private fun satAdjust(rgb: FloatArray, sat: Float) {
        val lum = luminance(rgb)
        rgb[0] = lum + (rgb[0] - lum) * sat
        rgb[1] = lum + (rgb[1] - lum) * sat
        rgb[2] = lum + (rgb[2] - lum) * sat
    }

    private fun desaturate(rgb: FloatArray) {
        val lum = luminance(rgb)
        rgb[0] = lum; rgb[1] = lum; rgb[2] = lum
    }

    private fun sCurve(rgb: FloatArray, strength: Float) {
        for (i in 0..2) {
            val x = rgb[i].coerceIn(0f, 1f)
            val curved = x * x * (3f - 2f * x)
            rgb[i] = x + (curved - x) * strength
        }
    }

    private fun gamma(rgb: FloatArray, g: Float) {
        val inv = 1f / g
        for (i in 0..2) rgb[i] = rgb[i].coerceIn(0.0001f, 1f).pow(inv)
    }

    private fun liftBlacks(rgb: FloatArray, amount: Float) {
        for (i in 0..2) rgb[i] = rgb[i] * (1f - amount) + amount
    }

    private fun crushHighlights(rgb: FloatArray, amount: Float) {
        for (i in 0..2) rgb[i] = rgb[i] * (1f - amount)
    }

    private fun channelBoost(rgb: FloatArray, ch: Int, amount: Float) {
        rgb[ch] = rgb[ch] + amount
    }

    private fun channelDim(rgb: FloatArray, ch: Int, amount: Float) {
        rgb[ch] = rgb[ch] - amount
    }

    private fun splitTone(
        rgb: FloatArray,
        shadowColor: FloatArray,
        highlightColor: FloatArray,
        strength: Float
    ) {
        val lum = luminance(rgb)
        val shadowWeight = (1f - lum * 2f).coerceIn(0f, 1f) * strength
        val highlightWeight = (lum * 2f - 1f).coerceIn(0f, 1f) * strength
        for (i in 0..2) {
            rgb[i] = rgb[i] + shadowColor[i] * shadowWeight +
                    (highlightColor[i] - 1f) * highlightWeight
        }
    }
}
