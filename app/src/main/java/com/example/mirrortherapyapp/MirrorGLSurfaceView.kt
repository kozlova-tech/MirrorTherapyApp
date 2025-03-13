package com.example.mirrortherapyapp

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat

class MirrorGLSurfaceView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {
    val renderer: MirrorRenderer

    init {
        setEGLContextClientVersion(2)
        // Request 8 bits for red, green, blue, and alpha, with a 16-bit depth buffer.
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        renderer = MirrorRenderer(context)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun getSurfaceProvider(): Preview.SurfaceProvider {
        return Preview.SurfaceProvider { request ->
            val surface = renderer.getSurface()
            request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { }
        }
    }

    // New method: expose the texture ID from the renderer.
    fun getTextureId(): Int {
        return renderer.getTextureId()
    }
}
