package com.example.mirrortherapyapp

import android.content.Context
import android.graphics.EmbossMaskFilter
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton

class EmbossButton(context: Context, attrs: AttributeSet) : AppCompatButton(context, attrs) {
    init {
        // EmbossMaskFilter parameters:
        // - directions: light source vector (x, y, z)
        // - ambient: ambient light between 0 and 1.
        // - specular: specular highlights (higher = more pronounced)
        // - blurRadius: blur radius for the highlight.
        val directions = floatArrayOf(1f, 1f, 1f)
        val ambient = 0.5f
        val specular = 8f
        val blurRadius = 3.5f
        val emboss = EmbossMaskFilter(directions, ambient, specular, blurRadius)
        paint.maskFilter = emboss

        // Disable hardware acceleration for this view to get the Emboss effect.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
}
