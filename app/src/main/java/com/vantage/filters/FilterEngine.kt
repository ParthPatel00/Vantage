package com.vantage.filters

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.vantage.camera.standard.AspectRatioManager
import com.vantage.models.FilterType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * High-performance OpenGL renderer for filters and real-time adjustments.
 */
class FilterEngine : GLSurfaceView.Renderer {

    private var program = 0
    private var vertexBuffer: FloatBuffer
    private var texCoordBuffer: FloatBuffer

    // Current state
    private var cameraTextureId = -1
    private var currentFilter = FilterType.NATURAL
    private var currentRatio = AspectRatioManager.AspectRatio.RATIO_4_3
    private var streamSize = android.util.Size(1920, 1080)
    private var sensorOrientation = 90
    private var isMirrored = false
    private var brightness = 0.0f
    private var contrast = 1.0f
    private var saturation = 1.0f
    private var gamma = 1.0f

    private val vertices = floatArrayOf(
        -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f
    )
    private val texCoords = floatArrayOf(
        0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f
    )

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBuffer.position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        updateProgram()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0 || cameraTextureId == -1) return

        GLES20.glUseProgram(program)

        // Bind OES Texture
        val uTexHandle = GLES20.glGetUniformLocation(program, "uTexture")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(uTexHandle, 0)

        // Set vertices
        val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        // Set texture coords
        val texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        // Set Adjustment Uniforms
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBrightness"), brightness)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uContrast"), contrast)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSaturation"), saturation)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uGamma"), gamma)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun setFilter(filter: FilterType) {
        currentFilter = filter
        updateProgram()
    }

    fun setAdjustments(brightness: Float, contrast: Float, saturation: Float, gamma: Float) {
        this.brightness = brightness
        this.contrast = contrast
        this.saturation = saturation
        this.gamma = gamma
    }

    fun setAspectRatio(ratio: AspectRatioManager.AspectRatio) {
        currentRatio = ratio
        updateTexCoords()
    }

    fun setStreamSize(size: android.util.Size) {
        streamSize = size
        updateTexCoords()
    }

    fun setSensorOrientation(orientation: Int, mirrored: Boolean = false) {
        sensorOrientation = orientation
        isMirrored = mirrored
        updateTexCoords()
    }

    private fun updateTexCoords() {
        // Full screen Center-Crop Fill
        // Screen is usually ~19.5:9 or taller, Sensor is 4:3.
        
        val screenRatio = 9f / 19.5f // Portrait: W/H
        val sensorRatio = 3f / 4f     // Portrait: W/H (90 deg rotated)
        
        val left = 0f
        val right = 1f
        val heightToKeep = screenRatio / sensorRatio
        val top = (1f - heightToKeep) / 2f
        val bottom = 1f - top

        // Rotate based on sensor orientation and applies the crop
        val rotatedCoords = when (sensorOrientation) {
            90 -> floatArrayOf(
                right, bottom, right, top, left, bottom, left, top
            )
            270 -> floatArrayOf(
                left, top, left, bottom, right, top, right, bottom
            )
            180 -> floatArrayOf(
                right, top, left, top, right, bottom, left, bottom
            )
            else -> floatArrayOf(
                left, bottom, right, bottom, left, top, right, top
            )
        }

        if (isMirrored) {
            // Mirror horizontally on the SCREEN.
            if (sensorOrientation == 90 || sensorOrientation == 270) {
                // Flip Sensor Y (which is Screen X after rotation)
                for (i in 0 until 4) {
                    val idx = i * 2 + 1
                    rotatedCoords[idx] = 1f - rotatedCoords[idx]
                }
            } else {
                // Flip Sensor X (which is Screen X for 0/180)
                for (i in 0 until 4) {
                    val idx = i * 2
                    rotatedCoords[idx] = 1f - rotatedCoords[idx]
                }
            }
        }
        
        texCoordBuffer.clear()
        texCoordBuffer.put(rotatedCoords)
        texCoordBuffer.position(0)
    }

    fun setCameraTextureId(id: Int) {
        cameraTextureId = id
    }

    private fun updateProgram() {
        val fragSource = when (currentFilter) {
            FilterType.NATURAL -> Shaders.NATURAL_FRAG
            FilterType.WARM -> Shaders.WARM_FRAG
            FilterType.COOL -> Shaders.COOL_FRAG
            FilterType.NOIR -> Shaders.NOIR_FRAG
            FilterType.VIVID -> Shaders.VIVID_FRAG
            FilterType.CINEMATIC -> Shaders.CINEMATIC_FRAG
            FilterType.VINTAGE -> Shaders.VINTAGE_FRAG
            FilterType.MUTED -> Shaders.MUTED_FRAG
            FilterType.FADE -> Shaders.FADE_FRAG
            FilterType.DRAMATIC -> Shaders.DRAMATIC_FRAG
            FilterType.SILVERTONE -> Shaders.SILVERTONE_FRAG
            FilterType.MONO -> Shaders.MONO_FRAG
        }
        
        program = createProgram(Shaders.VERTEX_SHADER, fragSource)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertexShader == 0 || fragmentShader == 0) return 0

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e("FilterEngine", "Could not link program")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e("FilterEngine", "Could not compile shader $shaderType")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
