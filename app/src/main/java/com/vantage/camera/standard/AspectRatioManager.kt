package com.vantage.camera.standard

import android.util.Size
import android.util.Rational
import kotlin.math.abs

/**
 * Manages aspect ratio calculations and sizing for camera streams.
 */
object AspectRatioManager {

    enum class AspectRatio(val rational: Rational) {
        RATIO_4_3(Rational(4, 3)),
        RATIO_16_9(Rational(16, 9)),
        RATIO_1_1(Rational(1, 1))
    }

    /**
     * Finds the best size for a given aspect ratio from the available sizes.
     */
    fun getOptimalSize(availableSizes: Array<Size>, ratio: AspectRatio, maxWidth: Int, maxHeight: Int): Size {
        val targetRatio = ratio.rational.toFloat()
        
        // Filter sizes that match the aspect ratio and are within constraints
        val matchingSizes = availableSizes.filter { 
            val sizeRatio = it.width.toFloat() / it.height.toFloat()
            abs(sizeRatio - targetRatio) < 0.01 && it.width <= maxWidth && it.height <= maxHeight
        }

        if (matchingSizes.isNotEmpty()) {
            return matchingSizes.maxByOrNull { it.width * it.height }!!
        }

        // If no exact match, return the largest size and we'll crop later in OpenGL
        return availableSizes.maxByOrNull { it.width * it.height }!!
    }

    private fun Rational.toFloat(): Float = numerator.toFloat() / denominator.toFloat()
}
