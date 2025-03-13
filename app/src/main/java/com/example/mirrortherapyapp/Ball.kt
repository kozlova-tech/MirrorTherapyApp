package com.example.mirrortherapyapp

import android.graphics.Matrix
import android.graphics.RadialGradient

data class Ball(
    var x: Float,
    var y: Float,
    var velocityY: Float,
    val radius: Float,
    val color: Int,
    var scale: Float = 1.0f,       // For pop animation
    var isHit: Boolean = false,    // Marks if the ball has been hit
    var shader: RadialGradient? = null,  // Preallocated shader (RadialGradient)
    var shaderMatrix: Matrix = Matrix()    // Preallocated Matrix for updating shader position
)
