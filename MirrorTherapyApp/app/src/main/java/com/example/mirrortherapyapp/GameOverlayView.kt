package com.example.mirrortherapyapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class GameOverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val ballPaint = Paint().apply {
        color = Color.RED
        isAntiAlias = true
    }
    private var ballX = 200f
    private var ballY = 200f
    private val ballRadius = 50f
    private var ballVelocityX = 8f
    private var ballVelocityY = 8f

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 16L // roughly 60 frames per second

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateBall()
            invalidate()
            handler.postDelayed(this, updateInterval)
        }
    }

    init {
        // Start the animation loop
        handler.post(updateRunnable)
    }

    private fun updateBall() {
        ballX += ballVelocityX
        ballY += ballVelocityY

        // Bounce off the view edges
        if (ballX - ballRadius < 0 || ballX + ballRadius > width) {
            ballVelocityX = -ballVelocityX
        }
        if (ballY - ballRadius < 0 || ballY + ballRadius > height) {
            ballVelocityY = -ballVelocityY
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // Add an impulse to the ball in the opposite direction of the touch
            ballVelocityX += (ballX - event.x) / 50
            ballVelocityY += (ballY - event.y) / 50
            return true
        }
        return super.onTouchEvent(event)
    }
}
