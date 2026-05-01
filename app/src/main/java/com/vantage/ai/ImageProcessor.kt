package com.vantage.ai

import android.graphics.Bitmap
import com.vantage.models.FilterType
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageExposureFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGammaFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHighlightShadowFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageMonochromeFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageRGBFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToneCurveFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVignetteFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter
import kotlin.math.abs

object ImageProcessor {

    fun process(source: Bitmap, analysis: SceneAnalysis, context: android.content.Context): Bitmap {
        val isAllDefaults = analysis.brightness == 0f && analysis.contrast == 1f &&
                analysis.saturation == 1f && analysis.gamma == 1f

        val preset = if (analysis.filter == FilterType.NATURAL && isAllDefaults) {
            StylePresets.defaultEnhancement
        } else {
            StylePresets.presets[analysis.filter]
        }

        val brightness: Float
        val contrast: Float
        val saturation: Float
        val gamma: Float

        if (preset != null && isAllDefaults) {
            brightness = preset.brightness
            contrast = preset.contrast
            saturation = preset.saturation
            gamma = preset.gamma
        } else if (preset != null) {
            brightness = if (abs(analysis.brightness) > abs(preset.brightness)) analysis.brightness else preset.brightness
            contrast = if (preset.contrast > 1f) maxOf(analysis.contrast, preset.contrast) else minOf(analysis.contrast, preset.contrast)
            saturation = if (preset.saturation < 1f) minOf(analysis.saturation, preset.saturation) else maxOf(analysis.saturation, preset.saturation)
            gamma = maxOf(analysis.gamma, preset.gamma)
        } else {
            brightness = analysis.brightness
            contrast = analysis.contrast
            saturation = analysis.saturation
            gamma = analysis.gamma
        }

        val filters = GPUImageFilterGroup()

        // Style-specific filter chain (tone curves, color grading, vignette, etc.)
        addStyleFilters(filters, analysis.filter)

        // Exposure / brightness
        if (brightness != 0f) {
            filters.addFilter(GPUImageExposureFilter(brightness))
        }

        // Contrast via tone curve (S-curve is more natural than linear contrast)
        if (contrast != 1f) {
            addContrastCurve(filters, contrast)
        }

        // Saturation
        if (saturation != 1f) {
            filters.addFilter(GPUImageSaturationFilter(saturation))
        }

        // Gamma
        if (gamma != 1f) {
            filters.addFilter(GPUImageGammaFilter(gamma))
        }

        // Highlight/shadow recovery for a polished look
        filters.addFilter(GPUImageHighlightShadowFilter(0f, 0.05f))

        // Subtle sharpening for detail
        filters.addFilter(GPUImageSharpenFilter(0.3f))

        val gpuImage = GPUImage(context)
        gpuImage.setImage(source)
        gpuImage.setFilter(filters)
        return gpuImage.bitmapWithFilterApplied
    }

    private fun addContrastCurve(group: GPUImageFilterGroup, contrast: Float) {
        val curve = GPUImageToneCurveFilter()
        // S-curve: pull shadows down, push highlights up proportional to contrast
        val strength = (contrast - 1f).coerceIn(0f, 1f)
        val shadowY = (0.25f - 0.08f * strength).coerceIn(0.1f, 0.25f)
        val highlightY = (0.75f + 0.08f * strength).coerceIn(0.75f, 0.9f)
        curve.setRgbCompositeControlPoints(arrayOf(
            android.graphics.PointF(0f, 0f),
            android.graphics.PointF(0.25f, shadowY),
            android.graphics.PointF(0.75f, highlightY),
            android.graphics.PointF(1f, 1f)
        ))
        group.addFilter(curve)
    }

    private fun addStyleFilters(group: GPUImageFilterGroup, filter: FilterType) {
        when (filter) {
            FilterType.CINEMATIC -> {
                // Teal-orange split tone via tone curves
                val curve = GPUImageToneCurveFilter()
                // Lift blue in shadows, warm the highlights
                curve.setBlueControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.1f),
                    android.graphics.PointF(0.25f, 0.32f),
                    android.graphics.PointF(0.75f, 0.68f),
                    android.graphics.PointF(1f, 0.88f)
                ))
                // Push red/warmth in highlights
                curve.setRedControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0f),
                    android.graphics.PointF(0.25f, 0.22f),
                    android.graphics.PointF(0.75f, 0.80f),
                    android.graphics.PointF(1f, 1f)
                ))
                // Slight S-curve for drama
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.02f),
                    android.graphics.PointF(0.25f, 0.18f),
                    android.graphics.PointF(0.75f, 0.82f),
                    android.graphics.PointF(1f, 0.98f)
                ))
                group.addFilter(curve)
                group.addFilter(GPUImageSaturationFilter(0.80f))
                group.addFilter(GPUImageVignetteFilter(android.graphics.PointF(0.5f, 0.5f), floatArrayOf(0f, 0f, 0f), 0.3f, 0.75f))
                group.addFilter(GPUImageWhiteBalanceFilter(5800f, 0f))
            }

            FilterType.VINTAGE -> {
                val curve = GPUImageToneCurveFilter()
                // Raised blacks (faded film look)
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.08f),
                    android.graphics.PointF(0.20f, 0.22f),
                    android.graphics.PointF(0.80f, 0.82f),
                    android.graphics.PointF(1f, 0.95f)
                ))
                // Warm shift: boost red, reduce blue
                curve.setRedControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.02f),
                    android.graphics.PointF(0.5f, 0.56f),
                    android.graphics.PointF(1f, 1f)
                ))
                curve.setBlueControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.05f),
                    android.graphics.PointF(0.5f, 0.42f),
                    android.graphics.PointF(1f, 0.85f)
                ))
                group.addFilter(curve)
                group.addFilter(GPUImageSaturationFilter(0.70f))
                group.addFilter(GPUImageWhiteBalanceFilter(6200f, 1f))
                group.addFilter(GPUImageVignetteFilter(android.graphics.PointF(0.5f, 0.5f), floatArrayOf(0f, 0f, 0f), 0.25f, 0.80f))
            }

            FilterType.DRAMATIC -> {
                val curve = GPUImageToneCurveFilter()
                // Deep S-curve for intense contrast
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0f),
                    android.graphics.PointF(0.20f, 0.10f),
                    android.graphics.PointF(0.50f, 0.50f),
                    android.graphics.PointF(0.80f, 0.92f),
                    android.graphics.PointF(1f, 1f)
                ))
                // Cool-toned shadows
                curve.setBlueControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.08f),
                    android.graphics.PointF(0.25f, 0.30f),
                    android.graphics.PointF(0.75f, 0.72f),
                    android.graphics.PointF(1f, 0.95f)
                ))
                group.addFilter(curve)
                group.addFilter(GPUImageSaturationFilter(0.80f))
                group.addFilter(GPUImageHighlightShadowFilter(0f, 0.15f))
                group.addFilter(GPUImageVignetteFilter(android.graphics.PointF(0.5f, 0.5f), floatArrayOf(0f, 0f, 0f), 0.4f, 0.65f))
            }

            FilterType.NOIR -> {
                group.addFilter(GPUImageMonochromeFilter(1f, floatArrayOf(0.6f, 0.45f, 0.3f, 1f)))
                val curve = GPUImageToneCurveFilter()
                // Heavy S-curve for high contrast B&W
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0f),
                    android.graphics.PointF(0.15f, 0.05f),
                    android.graphics.PointF(0.50f, 0.52f),
                    android.graphics.PointF(0.85f, 0.95f),
                    android.graphics.PointF(1f, 1f)
                ))
                group.addFilter(curve)
                group.addFilter(GPUImageVignetteFilter(android.graphics.PointF(0.5f, 0.5f), floatArrayOf(0f, 0f, 0f), 0.5f, 0.60f))
            }

            FilterType.VIVID -> {
                val curve = GPUImageToneCurveFilter()
                // Mild S-curve to pop
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0f),
                    android.graphics.PointF(0.25f, 0.20f),
                    android.graphics.PointF(0.75f, 0.82f),
                    android.graphics.PointF(1f, 1f)
                ))
                group.addFilter(curve)
                group.addFilter(GPUImageSaturationFilter(1.45f))
                group.addFilter(GPUImageHighlightShadowFilter(0f, 0.08f))
            }

            FilterType.WARM -> {
                group.addFilter(GPUImageWhiteBalanceFilter(6500f, 1f))
                group.addFilter(GPUImageRGBFilter(1.08f, 1.02f, 0.92f))
                val curve = GPUImageToneCurveFilter()
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.01f),
                    android.graphics.PointF(0.25f, 0.23f),
                    android.graphics.PointF(0.75f, 0.79f),
                    android.graphics.PointF(1f, 1f)
                ))
                group.addFilter(curve)
                group.addFilter(GPUImageVignetteFilter(android.graphics.PointF(0.5f, 0.5f), floatArrayOf(0.2f, 0.1f, 0f), 0.15f, 0.85f))
            }

            FilterType.COOL -> {
                group.addFilter(GPUImageWhiteBalanceFilter(4800f, 0f))
                group.addFilter(GPUImageRGBFilter(0.92f, 0.98f, 1.08f))
                val curve = GPUImageToneCurveFilter()
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.02f),
                    android.graphics.PointF(0.25f, 0.24f),
                    android.graphics.PointF(0.75f, 0.78f),
                    android.graphics.PointF(1f, 0.98f)
                ))
                group.addFilter(curve)
            }

            FilterType.MUTED -> {
                val curve = GPUImageToneCurveFilter()
                // Raised blacks + lowered whites for compressed, pastel look
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.10f),
                    android.graphics.PointF(0.30f, 0.32f),
                    android.graphics.PointF(0.70f, 0.72f),
                    android.graphics.PointF(1f, 0.92f)
                ))
                group.addFilter(curve)
                group.addFilter(GPUImageSaturationFilter(0.55f))
                group.addFilter(GPUImageExposureFilter(0.08f))
            }

            FilterType.FADE -> {
                val curve = GPUImageToneCurveFilter()
                // Heavily raised blacks for washed-out look
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.15f),
                    android.graphics.PointF(0.25f, 0.30f),
                    android.graphics.PointF(0.75f, 0.78f),
                    android.graphics.PointF(1f, 0.90f)
                ))
                group.addFilter(curve)
                group.addFilter(GPUImageSaturationFilter(0.75f))
                group.addFilter(GPUImageExposureFilter(0.10f))
            }

            FilterType.MONO -> {
                group.addFilter(GPUImageMonochromeFilter(1f, floatArrayOf(0.6f, 0.45f, 0.3f, 1f)))
                val curve = GPUImageToneCurveFilter()
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.02f),
                    android.graphics.PointF(0.25f, 0.22f),
                    android.graphics.PointF(0.75f, 0.80f),
                    android.graphics.PointF(1f, 0.98f)
                ))
                group.addFilter(curve)
            }

            FilterType.SILVERTONE -> {
                group.addFilter(GPUImageSaturationFilter(0.08f))
                group.addFilter(GPUImageRGBFilter(0.95f, 0.97f, 1.05f))
                val curve = GPUImageToneCurveFilter()
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0.03f),
                    android.graphics.PointF(0.25f, 0.24f),
                    android.graphics.PointF(0.75f, 0.78f),
                    android.graphics.PointF(1f, 0.97f)
                ))
                group.addFilter(curve)
                group.addFilter(GPUImageVignetteFilter(android.graphics.PointF(0.5f, 0.5f), floatArrayOf(0f, 0f, 0f), 0.20f, 0.82f))
            }

            FilterType.NATURAL -> {
                val curve = GPUImageToneCurveFilter()
                curve.setRgbCompositeControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0f),
                    android.graphics.PointF(0.20f, 0.16f),
                    android.graphics.PointF(0.50f, 0.52f),
                    android.graphics.PointF(0.80f, 0.86f),
                    android.graphics.PointF(1f, 1f)
                ))
                curve.setRedControlPoints(arrayOf(
                    android.graphics.PointF(0f, 0f),
                    android.graphics.PointF(0.50f, 0.52f),
                    android.graphics.PointF(1f, 1f)
                ))
                group.addFilter(curve)
                group.addFilter(GPUImageWhiteBalanceFilter(5200f, 0f))
                group.addFilter(GPUImageHighlightShadowFilter(0.05f, 0.10f))
            }
        }
    }
}
