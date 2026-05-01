package com.vantage.camera.standard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Standardized Camera2 manager following the patterns of 'camera-samples/Camera2Basic'.
 */
class StandardCameraManager(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * Starts the background thread for camera operations.
     */
    fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    /**
     * Stops the background thread.
     */
    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e("StandardCameraManager", "Interrupted while stopping background thread", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(cameraId: String, ratio: AspectRatioManager.AspectRatio, surfaceTexture: SurfaceTexture, onReady: (android.util.Size) -> Unit) {
        Log.d("StandardCameraManager", "Requesting camera open for ID: $cameraId")
        
        // Close existing camera first
        closeCamera()

        if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
            Log.e("StandardCameraManager", "Failed to acquire camera lock")
            return
        }

        try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val availableSizes = configMap?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
            
            // Optimal size for hardware stream
            val optimalSize = AspectRatioManager.getOptimalSize(availableSizes, ratio, 1920, 1080)
            Log.d("StandardCameraManager", "Opening camera $cameraId with optimal size: ${optimalSize.width}x${optimalSize.height}")

            surfaceTexture.setDefaultBufferSize(optimalSize.width, optimalSize.height)
            val surface = Surface(surfaceTexture)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    cameraDevice = camera
                    createCaptureSession(camera, surface) {
                        onReady(optimalSize)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                    Log.e("StandardCameraManager", "Camera error: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            cameraOpenCloseLock.release()
            Log.e("StandardCameraManager", "Failed to open camera $cameraId", e)
        }
    }

    private fun createCaptureSession(device: CameraDevice, surface: Surface, onReady: () -> Unit) {
        try {
            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
            }

            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        // Default to continuous auto-focus
                        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        session.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
                        onReady()
                    } catch (e: CameraAccessException) {
                        Log.e("StandardCameraManager", "Failed to set repeating request", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("StandardCameraManager", "Capture session configuration failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("StandardCameraManager", "Failed to create capture session", e)
        }
    }

    fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    fun updateSettings(settings: AdvancedParameterHandler.ManualSettings, cameraId: String) {
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        
        AdvancedParameterHandler.applyManualSettings(builder, settings, chars)
        
        try {
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e("StandardCameraManager", "Failed to update settings", e)
        }
    }

    fun getSensorOrientation(cameraId: String): Int {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        return chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    }
}
