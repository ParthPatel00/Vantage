package com.vantage.filters

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.Surface
import com.vantage.camera.standard.AspectRatioManager
import com.vantage.models.FilterType
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView that hosts the FilterEngine and provides a Surface for the camera.
 */
class FilterViewport @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    private val filterEngine = FilterEngine()
    private var cameraTextureId = -1
    private var cameraSurfaceTexture: SurfaceTexture? = null
    
    var onSurfaceReady: ((SurfaceTexture) -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Create OES texture for camera
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        
        // Setup SurfaceTexture
        cameraSurfaceTexture = SurfaceTexture(cameraTextureId).apply {
            setOnFrameAvailableListener {
                requestRender()
            }
        }
        
        // Notify listener on main thread
        post {
            cameraSurfaceTexture?.let { 
                onSurfaceReady?.invoke(it)
            }
        }

        filterEngine.onSurfaceCreated(gl, config)
        filterEngine.setCameraTextureId(cameraTextureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        filterEngine.onSurfaceChanged(gl, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        cameraSurfaceTexture?.updateTexImage()
        // Note: FilterEngine needs to be updated to support OES texture input
        // For now we just call its standard draw
        filterEngine.onDrawFrame(gl)
    }

    fun setFilter(filter: FilterType) {
        queueEvent { filterEngine.setFilter(filter) }
    }

    fun setAdjustments(b: Float, c: Float, s: Float, g: Float = 1.0f) {
        queueEvent { 
            filterEngine.setAdjustments(b, c, s, g)
            requestRender()
        }
    }

    fun setAspectRatio(ratio: AspectRatioManager.AspectRatio) {
        queueEvent { 
            filterEngine.setAspectRatio(ratio)
            requestRender()
        }
    }

    fun setStreamSize(size: android.util.Size) {
        queueEvent {
            filterEngine.setStreamSize(size)
            requestRender()
        }
    }

    fun setSensorOrientation(orientation: Int, mirrored: Boolean = false) {
        queueEvent {
            filterEngine.setSensorOrientation(orientation, mirrored)
            requestRender()
        }
    }
}
