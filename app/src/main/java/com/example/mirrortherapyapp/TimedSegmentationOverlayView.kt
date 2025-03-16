package com.example.mirrortherapyapp

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.ImageView

class   TimedSegmentationOverlayView(context: Context, attrs: AttributeSet) : ImageView(context, attrs) {
    override fun onDraw(canvas: Canvas) {
        val drawStart = System.currentTimeMillis()
        super.onDraw(canvas)
        val drawTime = System.currentTimeMillis() - drawStart
        //android.util.Log.d("DrawTiming", "Segmentation overlay drawing took: $drawTime ms")
    }
}
