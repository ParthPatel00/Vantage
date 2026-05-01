package com.vantage.camera.standard

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Log

/**
 * Provides logic for identifying and switching between logical and physical lenses.
 * Follows the Multi-camera API patterns from official samples.
 */
class LensSwitchingProvider(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    data class LensInfo(
        val logicalId: String,
        val physicalId: String?,
        val label: String,
        val focalLength: Float,
        val facing: Int
    )

    /**
     * Enumerates all available lenses, expanding logical cameras into physical lenses where possible.
     */
    fun getAvailableLenses(): List<LensInfo> {
        val allLenses = mutableListOf<LensInfo>()
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
                
                val f = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f
                allLenses.add(LensInfo(id, null, if (facing == CameraMetadata.LENS_FACING_FRONT) "Selfie" else "Main", f, facing))
            }
        } catch (e: Exception) {
            Log.e("LensSwitchingProvider", "Failed to enumerate lenses", e)
        }
        
        val finalLenses = mutableListOf<LensInfo>()
        
        // Return exactly one back camera and one front camera
        allLenses.find { it.facing == CameraMetadata.LENS_FACING_BACK }?.let { finalLenses.add(it) }
        allLenses.find { it.facing == CameraMetadata.LENS_FACING_FRONT }?.let { finalLenses.add(it) }

        return finalLenses
    }

    private fun getLabelForFocalLength(focalLength: Float, facing: Int): String {
        // Obsolete, keeping for reference if needed
        return ""
    }
}
