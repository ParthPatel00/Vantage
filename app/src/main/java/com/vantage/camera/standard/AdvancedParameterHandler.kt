package com.vantage.camera.standard

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata

/**
 * Handles mapping of manual "RAW" parameters to Camera2 CaptureRequests.
 */
object AdvancedParameterHandler {

    data class ManualSettings(
        val iso: Int? = null,
        val shutterSpeedNs: Long? = null,
        val focusDistance: Float? = null,
        val zoomRatio: Float? = null,
        val sharpness: Int? = null,
        val denoiseMode: Int? = null,
        val stabilizationMode: Int? = null,
        val whiteBalanceMode: Int? = null,
        val exposureComp: Float? = null
    )

    fun applyManualSettings(builder: CaptureRequest.Builder, settings: ManualSettings, chars: CameraCharacteristics) {
        // 1. Exposure (ISO and Shutter OR EV)
        if (settings.iso != null || settings.shutterSpeedNs != null) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            settings.iso?.let { builder.set(CaptureRequest.SENSOR_SENSITIVITY, it) }
            settings.shutterSpeedNs?.let { builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, it) }
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            settings.exposureComp?.let {
                val step = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: android.util.Rational(1, 3)
                val value = (it / step.toFloat()).toInt()
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, value)
            }
        }

        // 2. Focus
        if (settings.focusDistance != null) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, settings.focusDistance)
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        // 3. Zoom
        settings.zoomRatio?.let {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, it)
        }

        // 4. White Balance
        settings.whiteBalanceMode?.let {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, it)
        }

        // 5. Edge Enhancement (Sharpness)
        settings.sharpness?.let {
            builder.set(CaptureRequest.EDGE_MODE, it)
        }

        // 6. Noise Reduction
        settings.denoiseMode?.let {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, it)
        }

        // 7. Stabilization
        settings.stabilizationMode?.let {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, it)
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, it)
        }
    }
}
