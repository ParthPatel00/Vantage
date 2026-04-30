package com.vantage.contracts

import android.graphics.SurfaceTexture
import android.net.Uri
import com.vantage.models.CameraSettings

interface ICameraEngine {
    fun openCamera(surfaceTexture: SurfaceTexture, onReady: () -> Unit)
    fun applySettings(settings: CameraSettings)
    fun capturePreviewFrame(onFrame: (filePath: String) -> Unit)
    fun capturePhoto(onSaved: (Uri) -> Unit)
    fun close()
}

