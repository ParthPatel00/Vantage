package com.vantage.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.vantage.models.FilterType
import kotlin.math.pow
import kotlin.math.roundToInt

object ImageProcessor {

    fun process(source: Bitmap, analysis: SceneAnalysis): Bitmap {
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)

        val combined = ColorMatrix()

        val satMatrix = ColorMatrix().apply { setSaturation(analysis.saturation) }
        combined.postConcat(satMatrix)

        val b = analysis.brightness * 255f
        val brightnessMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, b,
            0f, 1f, 0f, 0f, b,
            0f, 0f, 1f, 0f, b,
            0f, 0f, 0f, 1f, 0f
        ))
        combined.postConcat(brightnessMatrix)

        val c = analysis.contrast
        val t = (1f - c) * 127.5f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
        combined.postConcat(contrastMatrix)

        combined.postConcat(filterMatrix(analysis.filter))

        val canvas = Canvas(mutable)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(combined) }
        canvas.drawBitmap(mutable, 0f, 0f, paint)

        if (analysis.gamma != 1f) {
            applyGamma(mutable, analysis.gamma)
        }

        return mutable
    }

    private fun filterMatrix(filter: FilterType): ColorMatrix = when (filter) {
        FilterType.WARM -> ColorMatrix(floatArrayOf(
            1.2f, 0f, 0f, 0f, 10f,
            0f, 1.1f, 0f, 0f, 5f,
            0f, 0f, 0.9f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.COOL -> ColorMatrix(floatArrayOf(
            0.9f, 0f, 0f, 0f, -10f,
            0f, 1.0f, 0f, 0f, 5f,
            0f, 0f, 1.2f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.NOIR -> {
            val m = ColorMatrix()
            m.setSaturation(0f)
            val highContrast = ColorMatrix(floatArrayOf(
                1.5f, 0f, 0f, 0f, -40f,
                0f, 1.5f, 0f, 0f, -40f,
                0f, 0f, 1.5f, 0f, -40f,
                0f, 0f, 0f, 1f, 0f
            ))
            m.postConcat(highContrast)
            m
        }
        FilterType.VIVID -> {
            val m = ColorMatrix()
            m.setSaturation(1.4f)
            val boost = ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 5f,
                0f, 1.1f, 0f, 0f, 5f,
                0f, 0f, 1.1f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            ))
            m.postConcat(boost)
            m
        }
        FilterType.DRAMATIC -> {
            val m = ColorMatrix()
            m.setSaturation(0.8f)
            val dramatic = ColorMatrix(floatArrayOf(
                1.4f, 0f, 0f, 0f, -30f,
                0f, 1.4f, 0f, 0f, -30f,
                0f, 0f, 1.5f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            ))
            m.postConcat(dramatic)
            m
        }
        FilterType.CINEMATIC -> {
            val m = ColorMatrix(floatArrayOf(
                1.1f, 0f, 0.05f, 0f, -5f,
                0f, 1.0f, 0.05f, 0f, 0f,
                0.05f, 0.1f, 1.0f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
            val sat = ColorMatrix()
            sat.setSaturation(0.85f)
            m.postConcat(sat)
            m
        }
        FilterType.VINTAGE -> {
            val m = ColorMatrix(floatArrayOf(
                1.1f, 0.1f, 0f, 0f, 15f,
                0f, 1.0f, 0f, 0f, 10f,
                0f, 0f, 0.85f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            ))
            val sat = ColorMatrix()
            sat.setSaturation(0.8f)
            m.postConcat(sat)
            m
        }
        FilterType.MUTED -> {
            val m = ColorMatrix()
            m.setSaturation(0.6f)
            val soft = ColorMatrix(floatArrayOf(
                0.95f, 0f, 0f, 0f, 10f,
                0f, 0.95f, 0f, 0f, 10f,
                0f, 0f, 0.95f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
            m.postConcat(soft)
            m
        }
        FilterType.FADE -> ColorMatrix(floatArrayOf(
            0.9f, 0f, 0f, 0f, 25f,
            0f, 0.9f, 0f, 0f, 25f,
            0f, 0f, 0.9f, 0f, 25f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.MONO -> {
            val m = ColorMatrix()
            m.setSaturation(0f)
            m
        }
        FilterType.SILVERTONE -> {
            val m = ColorMatrix()
            m.setSaturation(0.1f)
            val cool = ColorMatrix(floatArrayOf(
                0.95f, 0f, 0f, 0f, 0f,
                0f, 0.95f, 0f, 0f, 0f,
                0f, 0f, 1.05f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            ))
            m.postConcat(cool)
            m
        }
        else -> ColorMatrix()
    }

    private fun applyGamma(bitmap: Bitmap, gamma: Float) {
        val lut = IntArray(256) { i ->
            (255f * (i / 255f).pow(1f / gamma)).roundToInt().coerceIn(0, 255)
        }

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p shr 24) and 0xFF
            val r = lut[(p shr 16) and 0xFF]
            val g = lut[(p shr 8) and 0xFF]
            val b = lut[p and 0xFF]
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
