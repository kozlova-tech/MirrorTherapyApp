package com.example.mirrortherapyapp

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.Surface
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MirrorRenderer(val context: Context) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private var textureId = -1
    private lateinit var surfaceTexture: SurfaceTexture
    @Volatile private var updateSurface = false

    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureHandle = 0
    private var mvpMatrixHandle = 0
    private var resolutionHandle = 0
    private var mirrorModeHandle = 0
    private val mvpMatrix = FloatArray(16)

    // Mirror mode: 0 = full; 1 = mirror left; 2 = mirror right.
    private var mirrorMode = 0

    // Vertex data for a full-screen quad (x, y, u, v)
    private val vertexData = floatArrayOf(
        -1f,  1f, 0f, 0f,
        -1f, -1f, 0f, 1f,
        1f,  1f, 1f, 0f,
        1f,  1f, 1f, 0f,
        -1f, -1f, 0f, 1f,
        1f, -1f, 1f, 1f
    )
    private val vertexBuffer = java.nio.ByteBuffer.allocateDirect(vertexData.size * 4)
        .order(java.nio.ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(vertexData)
            position(0)
        }

    fun getTextureId(): Int {
        return textureId
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        textureId = createExternalTexture()
        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setOnFrameAvailableListener(this)

        program = createProgram(vertexShaderCode, fragmentShaderCode)

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "sTexture")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        mirrorModeHandle = GLES20.glGetUniformLocation(program, "uMirrorMode")

        Matrix.setIdentityM(mvpMatrix, 0)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, width.toFloat(), height.toFloat())
    }

    override fun onDrawFrame(gl: GL10?) {
        synchronized(this) {
            if (updateSurface) {
                surfaceTexture.updateTexImage()
                updateSurface = false
            }
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1i(mirrorModeHandle, mirrorMode)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 4 * 4, vertexBuffer)

        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 4 * 4, vertexBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        updateSurface = true
    }

    // Returns a Surface wrapping the SurfaceTexture for CameraX.
    fun getSurface(): Surface {
        return Surface(surfaceTexture)
    }

    fun setMirrorMode(mode: Int) {
        // 0: Full, 1: Mirror Left, 2: Mirror Right.
        mirrorMode = mode
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        return program
    }

    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uMVPMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision highp float;
        uniform samplerExternalOES sTexture;
        uniform int uMirrorMode; // 0 = full, 1 = mirror left, 2 = mirror right.
        uniform vec2 uResolution; // resolution of the output (in pixels)
        varying vec2 vTexCoord;
        
        // Custom 2x2 box filter sampling function.
        vec4 boxFilter(vec2 tc) {
            vec2 offset = 1.0 / uResolution; // one pixel offset in texture coords
            // Sample four neighboring texels
            vec4 sample1 = texture2D(sTexture, tc + vec2(-offset.x, -offset.y));
            vec4 sample2 = texture2D(sTexture, tc + vec2( offset.x, -offset.y));
            vec4 sample3 = texture2D(sTexture, tc + vec2(-offset.x,  offset.y));
            vec4 sample4 = texture2D(sTexture, tc + vec2( offset.x,  offset.y));
            return (sample1 + sample2 + sample3 + sample4) * 0.25;
        }
        
        void main() {
            // Start with the interpolated texture coordinate.
            vec2 tc = vTexCoord;
            // Apply mirror logic based on mode.
            if(uMirrorMode == 1) {
                // Mirror Left: For tex coords in the right half (tc.x > 0.5), sample from the left half.
                if(tc.x > 0.5) {
                    float factor = (tc.x - 0.5) * 2.0; // factor goes 0 to 1 over right half
                    tc.x = (1.0 - factor) * 0.5; // mirror back into left half
                }
            } else if(uMirrorMode == 2) {
                // Mirror Right: For tex coords in the left half (tc.x < 0.5), sample from the right half.
                if(tc.x < 0.5) {
                    float factor = tc.x * 2.0; // factor goes 0 to 1 over left half
                    tc.x = 0.5 + (1.0 - factor) * 0.5; // mirror into right half
                }
            }
            // Use the custom box filter for smoother sampling.
            vec4 color = boxFilter(tc);
            gl_FragColor = color;
        }

    """.trimIndent()
}
