package com.example.mirrortherapyapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

// Data class for success images (loaded from assets)
data class SuccessImage(
    val bitmap: Bitmap,
    var x: Float,
    var y: Float,
    var timeRemaining: Long // milliseconds to display
)

class GameOverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    // Success image file names (placed in app/src/main/assets)
    private val successImageFiles = listOf(
        "wow1.png",
        "terrific1.png",
        "amazing1.png",
        "great1.png"
    )

    // Fixed colors for falling balls
    private val fixedColors = listOf(
        Color.RED,
        Color.BLUE,
        Color.YELLOW,
        Color.GREEN,
        Color.MAGENTA // pink-ish
    )

    // Control for ball spawning
    var isSpawningBalls = false

    // Callback to notify the Activity when a ball is tapped
    private var onBallTapListener: ((Int) -> Unit)? = null
    fun setOnBallTapListener(listener: (Int) -> Unit) {
        onBallTapListener = listener
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 16L // ~60 FPS

    private val balls = mutableListOf<Ball>()
    private val paint = Paint().apply { isAntiAlias = true }

    // Configuration for spawning balls
    private val spawnInterval = 700L
    private var lastSpawnTime = 0L

    // Ball properties
    private val ballRadius = 40f
    private val minVelocityY = 5f
    private val maxVelocityY = 12f

    // List for success images to be drawn (pop-up images)
    private val successImages = mutableListOf<SuccessImage>()

    // Variable to store the latest segmentation mask (from the analyzer)
    private var segmentationMask: Bitmap? = null

    // Update loop runnable (posted every ~16ms)
    private val updateRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            if (isSpawningBalls && (currentTime - lastSpawnTime >= spawnInterval)) {
                spawnBall()
                lastSpawnTime = currentTime
            }
            updateBalls()
            updateSuccessImages()
            checkSegmentationCollision()
            invalidate()
            handler.postDelayed(this, updateInterval)
        }
    }

    init {
        handler.post(updateRunnable)
    }

    // Public method to update the segmentation mask from the analyzer.
    fun updateSegmentationMask(mask: Bitmap?) {
        segmentationMask = mask
    }

    private fun spawnBall() {
        val xPosition = width / 2f
        val velocityY = Random.nextFloat() * (maxVelocityY - minVelocityY) + minVelocityY
        val color = fixedColors.random()
        val newBall = Ball(
            x = xPosition,
            y = -ballRadius,
            velocityY = velocityY,
            radius = ballRadius,
            color = color
        )
        // Preallocate a RadialGradient for the ball.
        newBall.shader = RadialGradient(
            newBall.radius,
            newBall.radius,
            newBall.radius,
            intArrayOf(newBall.color, darkenColor(newBall.color)),
            floatArrayOf(0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )
        balls.add(newBall)
    }

    private fun updateBalls() {
        for (ball in balls) {
            if (!ball.isHit) {
                ball.y += ball.velocityY
            }
        }
        balls.removeAll { it.y - it.radius > height }
    }

    private fun updateSuccessImages() {
        val iter = successImages.iterator()
        while (iter.hasNext()) {
            val si = iter.next()
            si.timeRemaining -= updateInterval
            if (si.timeRemaining <= 0) {
                iter.remove()
            }
        }
    }

    // New method: check if the segmentation mask touches any ball.
    private fun checkSegmentationCollision() {
        val mask = segmentationMask ?: return
        // Scale factors from view coordinates to segmentation mask coordinates.
        val scaleX = mask.width.toFloat() / width.toFloat()
        val scaleY = mask.height.toFloat() / height.toFloat()
        // Iterate over a copy of the balls list to avoid concurrent modification.
        for (ball in balls.toList()) {
            if (!ball.isHit) {
                val maskX = (ball.x * scaleX).toInt().coerceIn(0, mask.width - 1)
                val maskY = (ball.y * scaleY).toInt().coerceIn(0, mask.height - 1)
                val pixel = mask.getPixel(maskX, maskY)
                val alpha = Color.alpha(pixel)
                // If the mask indicates segmentation (alpha > 128), treat as collision.
                if (alpha > 128) {
                    ball.isHit = true
                    ValueAnimator.ofFloat(1.0f, 1.5f, 0f).apply {
                        duration = 300
                        addUpdateListener { animator ->
                            ball.scale = animator.animatedValue as Float
                            invalidate()
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                // Post the removal so that it happens after the current iteration.
                                Handler(Looper.getMainLooper()).post { balls.remove(ball) }
                            }
                        })
                        start()
                    }
                    onBallTapListener?.invoke(ball.color)
                }
            }
        }
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw each ball with its shader and current scale.
        for (ball in balls) {
            ball.shaderMatrix.setTranslate(ball.x - ball.radius, ball.y - ball.radius)
            ball.shader?.setLocalMatrix(ball.shaderMatrix)
            paint.shader = ball.shader
            canvas.drawCircle(ball.x, ball.y, ball.radius * ball.scale, paint)
        }
        paint.shader = null

        // Draw success images.
        for (si in successImages) {
            canvas.drawBitmap(si.bitmap, si.x, si.y, null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val touchX = event.x
            val touchY = event.y
            val tappedBalls = balls.filter { ball ->
                val dx = ball.x - touchX
                val dy = ball.y - touchY
                dx * dx + dy * dy <= (ball.radius * ball.scale) * (ball.radius * ball.scale)
            }
            if (tappedBalls.isNotEmpty()) {
                tappedBalls.forEach { ball ->
                    if (!ball.isHit) {
                        ball.isHit = true
                        ValueAnimator.ofFloat(1.0f, 1.5f, 0f).apply {
                            duration = 300
                            addUpdateListener { animator ->
                                ball.scale = animator.animatedValue as Float
                                invalidate()
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    balls.remove(ball)
                                }
                            })
                            start()
                        }
                        onBallTapListener?.invoke(ball.color)
                    }
                }
                return true
            }
            return false
        }
        return super.onTouchEvent(event)
    }

    // Show a success image from assets (pop-up image) on the sides.
    fun showSuccessImageFromAssets(displayDuration: Long = 1000L) {
        val fileName = successImageFiles.random()
        val bmp = loadBitmapFromAssets(fileName)
        if (bmp == null) {
            android.util.Log.e("GameOverlayView", "Failed to load image: $fileName")
            return
        }
        val screenWidth = width
        val screenHeight = height
        val leftMax = (screenWidth / 3) - bmp.width
        val rightMin = 2 * (screenWidth / 3)
        val rightMax = screenWidth - bmp.width
        val randomX: Float = if (Random.nextBoolean()) {
            if (leftMax > 0) Random.nextInt(leftMax.toInt()).toFloat() else 0f
        } else {
            if (rightMax > rightMin) Random.nextInt((rightMax - rightMin).toInt()).toFloat() + rightMin else rightMin.toFloat()
        }
        val maxY = (screenHeight - bmp.height).coerceAtLeast(0)
        val randomY = if (maxY > 0) Random.nextInt(maxY).toFloat() else 0f
        val si = SuccessImage(bmp, randomX, randomY, displayDuration)
        successImages.add(si)
    }

    private fun loadBitmapFromAssets(fileName: String): Bitmap? {
        return try {
            context.assets.open(fileName).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun darkenColor(color: Int, factor: Float = 0.7f): Int {
        val r = (Color.red(color) * factor).toInt().coerceAtMost(255)
        val g = (Color.green(color) * factor).toInt().coerceAtMost(255)
        val b = (Color.blue(color) * factor).toInt().coerceAtMost(255)
        return Color.rgb(r, g, b)
    }
}
