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
        "terrific1.png",
        "wow1.png",
        "great1.png",
        "amazing1.png"
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
    private var spawnInterval = 700L
    private var lastSpawnTime = 0L

    // Ball properties
    private var ballSizeMultiplier: Float = 1.0f // Default to normal size
    private val ballRadius = 40f * ballSizeMultiplier
    private var minVelocityY = 5f
    private var maxVelocityY = 12f

    // List for success images to be drawn (pop-up images)
    private val successImages = mutableListOf<SuccessImage>()

    // Variable to store the latest segmentation mask (from the analyzer)
    private var segmentationMask: Bitmap? = null

    // New fields to ensure target color frequency.
    private var totalBallsSpawned = 0
    private var targetBallsSpawned = 0
    private var stageTargetColor: Int? = null

    private var targetOffset: Int = 0
    private var currentMirrorMode: Int = 0  // 0: full, 1: left, 2: right


    fun setTargetOffset(offset: Int) {
        this.targetOffset = offset
    }

    fun setCurrentMirrorMode(mode: Int) {
        this.currentMirrorMode = mode
    }

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

    // Call this at the start of each stage to set/reset the target color.
    fun setTargetColor(color: Int) {
        stageTargetColor = color
        totalBallsSpawned = 0
        targetBallsSpawned = 0
    }

    private var ballPairCounter = 0

    private fun spawnBall() {
        totalBallsSpawned++
        // Initially choose a random color.
        var chosenColor = fixedColors.random()

        // If we have a target color defined, ensure that at least 1/3 are target-colored.
        stageTargetColor?.let { targetColor ->
            if (chosenColor == targetColor) {
                targetBallsSpawned++
            }
            val requiredTargetCount = Math.ceil(totalBallsSpawned / 3.0).toInt()
            if (targetBallsSpawned < requiredTargetCount && chosenColor != targetColor) {
                chosenColor = targetColor
                targetBallsSpawned++
            }
        }

        val center = width / 2f
        // Determine x position for the interactive ball based on mirror mode.
        val interactiveX = when (currentMirrorMode) {
            1 -> center - targetOffset  // left mirror
            2 -> center + targetOffset  // right mirror
            else -> center
        }
        val velocityY = Random.nextFloat() * (maxVelocityY - minVelocityY) + minVelocityY

        // If offset is applied and mirror mode is active, generate a pairId for both balls.
        val pairId = if (targetOffset > 0 && currentMirrorMode != 0) {
            val id = ballPairCounter
            ballPairCounter++  // increment once so both get the same id.
            id
        } else {
            null
        }

        // Create the interactive ball.
        val interactiveBall = Ball(
            x = interactiveX,
            y = -ballRadius,
            velocityY = velocityY,
            radius = ballRadius * ballSizeMultiplier,
            color = chosenColor,
            isMirror = false,
            pairId = pairId
        )
        interactiveBall.shader = RadialGradient(
            interactiveBall.radius,
            interactiveBall.radius,
            interactiveBall.radius,
            intArrayOf(interactiveBall.color, darkenColor(interactiveBall.color)),
            floatArrayOf(0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )
        balls.add(interactiveBall)

        // Create the mirrored copy for every ball if an offset is applied and mirror mode is active.
        if (targetOffset > 0 && currentMirrorMode != 0) {
            // Mirror x position: mirrorX = 2 * center - interactiveX.
            val mirrorX = 2 * center - interactiveX
            val mirrorBall = Ball(
                x = mirrorX,
                y = -ballRadius,
                velocityY = velocityY,
                radius = ballRadius,
                color = chosenColor,
                isMirror = true,
                pairId = pairId
            )
            mirrorBall.shader = RadialGradient(
                mirrorBall.radius,
                mirrorBall.radius,
                mirrorBall.radius,
                intArrayOf(mirrorBall.color, darkenColor(mirrorBall.color)),
                floatArrayOf(0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
            balls.add(mirrorBall)
        }
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
        val scaleX = mask.width.toFloat() / width
        val scaleY = mask.height.toFloat() / height
        // Work on a copy of the balls list to avoid concurrent modifications.
        for (ball in balls.toList()) {
            if (!ball.isHit) {
                val maskX = (ball.x * scaleX).toInt().coerceIn(0, mask.width - 1)
                val maskY = (ball.y * scaleY).toInt().coerceIn(0, mask.height - 1)
                val pixel = mask.getPixel(maskX, maskY)
                val alpha = Color.alpha(pixel)
                if (alpha > 128) {
                    // Mark as hit and start the removal animation.
                    ball.isHit = true
                    if (ball.pairId != null) {
                        // Gather all balls with the same pairId.
                        val group = balls.filter { it.pairId == ball.pairId }
                        ValueAnimator.ofFloat(1.0f, 1.5f, 0f).apply {
                            duration = 300
                            addUpdateListener { animator ->
                                val newScale = animator.animatedValue as Float
                                group.forEach { it.scale = newScale }
                                postInvalidateOnAnimation()
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    balls.removeAll { it.pairId == ball.pairId }
                                }
                            })
                            start()
                        }
                    } else {
                        // No pair, animate removal of this ball only.
                        ValueAnimator.ofFloat(1.0f, 1.5f, 0f).apply {
                            duration = 300
                            addUpdateListener { animator ->
                                ball.scale = animator.animatedValue as Float
                                postInvalidateOnAnimation()
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    balls.remove(ball)
                                }
                            })
                            start()
                        }
                    }
                    // Optionally, invoke the ball tap listener.
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
                if (ball.isMirror) return@filter false
                val dx = ball.x - touchX
                val dy = ball.y - touchY
                dx * dx + dy * dy <= (ball.radius * ball.scale).let { it * it }
            }
            val processedPairIds = mutableSetOf<Int>()
            if (tappedBalls.isNotEmpty()) {
                tappedBalls.forEach { ball ->
                    if (!ball.isHit) {
                        ball.isHit = true
                        // If the ball has a pairId, animate all balls sharing that pair
                        ball.pairId?.let { id ->
                            if (!processedPairIds.contains(id)) {
                                processedPairIds.add(id)
                                val group = balls.filter { it.pairId == id }
                                ValueAnimator.ofFloat(1.0f, 1.5f, 0f).apply {
                                    duration = 300
                                    startDelay = 0
                                    addUpdateListener { animator ->
                                        val newScale = animator.animatedValue as Float
                                        group.forEach { it.scale = newScale }
                                        // Force a synchronous redraw on the next frame.
                                        postInvalidateOnAnimation()
                                    }
                                    addListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            balls.removeAll { it.pairId == id }
                                        }
                                    })
                                    start()
                                }
                            }
                        } ?: run {
                            // Animate individual ball (if no mirror pair exists).
                            ValueAnimator.ofFloat(1.0f, 1.5f, 0f).apply {
                                duration = 300
                                startDelay = 0
                                addUpdateListener { animator ->
                                    ball.scale = animator.animatedValue as Float
                                    postInvalidateOnAnimation()
                                }
                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        balls.remove(ball)
                                    }
                                })
                                start()
                            }
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
    fun showSuccessImageFromAssets(index: Int, displayDuration: Long = 1000L) {
        val fileName = successImageFiles[index]
        // Load the bitmap from assets.
        val bmp = loadBitmapFromAssets(fileName)
        if (bmp == null) {
            android.util.Log.e("GameOverlayView", "Failed to load image: $fileName")
            return
        }
        // Retrieve bitmap dimensions.
        val bmpWidth = bmp.width
        val bmpHeight = bmp.height

        val screenWidth = width
        val screenHeight = height

        // Calculate allowed horizontal intervals:
        // Left side: from 0 to (screenWidth/3 - bmpWidth)
        // Right side: from (2*screenWidth/3) to (screenWidth - bmpWidth)
        val leftMax = (screenWidth / 3) - bmpWidth
        val rightMin = 2 * (screenWidth / 3)
        val rightMax = screenWidth - bmpWidth

        val randomX: Float = if (Random.nextBoolean()) {
            if (leftMax > 0) Random.nextInt(leftMax.toInt()).toFloat() else 0f
        } else {
            if (rightMax > rightMin) Random.nextInt((rightMax - rightMin).toInt()).toFloat() + rightMin else rightMin.toFloat()
        }
        val maxY = (screenHeight - bmpHeight).coerceAtLeast(0)
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

    fun clearBalls() {
        // Assuming 'balls' is your mutable list of Ball objects:
        balls.clear()
        invalidate() // Refresh the view
    }

    // Setter for difficulty that affects the velocity range.
    // Expected difficulty values: "Easy", "Medium", "Hard".
    fun setDifficulty(difficulty: String) {
        when (difficulty.toLowerCase()) {
            "easy" -> {
                // Slower falling balls for easy difficulty.
                minVelocityY = 3f// 2f
                maxVelocityY = 3f//4f
                spawnInterval = 3600L // Longer interval = fewer balls.
            }
            "hard" -> {
                // Faster falling balls for hard difficulty.
                minVelocityY = 25f//20f
                maxVelocityY = 25f//30f
                spawnInterval = 800L // Shorter interval = more balls.
            }
            else -> { // "medium" or any other value defaults to medium behavior.
                minVelocityY = 7f//5f
                maxVelocityY = 7f//12f
                spawnInterval = 1600L
            }
        }
    }

    fun setBallSizeMultiplier(multiplier: Float) {
        ballSizeMultiplier = multiplier
        invalidate() // Redraw the view so that changes take effect immediately
    }

}
