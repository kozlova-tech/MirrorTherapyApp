package com.example.mirrortherapyapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Size
import android.widget.PopupMenu
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var mirrorGLSurfaceView: MirrorGLSurfaceView
    private lateinit var gameOverlayView: GameOverlayView
    private lateinit var segmentationOverlayView: ImageView

    private lateinit var textViewTargetColor: TextView
    private lateinit var textViewTimer: TextView
    private lateinit var textViewDifficulty: TextView
    private lateinit var textViewSuccess: TextView
    private lateinit var textViewMiss: TextView
    private lateinit var textViewGetReady: TextView

    private lateinit var radioFull: RadioButton
    private lateinit var radioMirrorLeft: RadioButton
    private lateinit var radioMirrorRight: RadioButton
    private lateinit var modeSelector: RadioGroup

    // New cog icon ImageView (already in layout as imgCog)
    private lateinit var imgCog: ImageView

    private var segmentationEnabled = true
    private lateinit var mediaPlayer: MediaPlayer

    // --- New SoundPool fields for short sound effects ---
    private lateinit var soundPool: SoundPool
    private var soundThree: Int = 0
    private var soundTwo: Int = 0
    private var soundOne: Int = 0
    private var soundGo: Int = 0
    private var soundMiss: Int = 0
    private lateinit var successSoundIds: List<Int>
    private lateinit var countdownSoundIds: Map<Int, Int>
    // -----------------------------------------------------

    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    private val fixedColors = listOf(
        Color.RED,
        Color.BLUE,
        Color.YELLOW,
        Color.GREEN,
        Color.MAGENTA
    )

    // These original MediaPlayer-based short sound IDs are no longer used.
    private val successSounds = listOf(
        R.raw.terrific,
        R.raw.wow,
        R.raw.great,
        R.raw.amazing
    )
    private val countdownSounds = mapOf(
        3 to R.raw.three,
        2 to R.raw.two,
        1 to R.raw.one,
        0 to R.raw.go
    )

    private var stageTargetColor: Int = Color.RED
    private var successCount = 0
    private var missCount = 0
    private var stageDurationMillis = 10_000L
    private var remainingTimeMillis: Long = 0L
    private var stageTimer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val segmentationExecutor = Executors.newSingleThreadExecutor()

    private var currentUser: User? = null

    // Handler for scheduling auto-orientation checks.
    private val autoOrientationHandler = Handler(Looper.getMainLooper())
    // Variable to store the latest segmentation mask.
    @Volatile
    private var latestSegmentationMask: Bitmap? = null
    // Current mirror mode (0: full, 1: mirror left, 2: mirror right).
    private var currentMirrorMode: Int = 2 // Default to Right Mirrored.
    // A threshold below which we consider there are no lit foreground pixels.
    private val pixelCountThreshold = 300 // adjust as needed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mirrorGLSurfaceView = findViewById(R.id.mirrorGLSurfaceView)
        gameOverlayView = findViewById(R.id.gameOverlayView)
        segmentationOverlayView = findViewById(R.id.segmentationOverlayView)
        textViewTargetColor = findViewById(R.id.textViewTargetColor)
        textViewTimer = findViewById(R.id.textViewTimer)
        textViewDifficulty = findViewById(R.id.textViewDifficulty)
        textViewSuccess = findViewById(R.id.textViewSuccess)
        textViewMiss = findViewById(R.id.textViewMiss)
        textViewGetReady = findViewById(R.id.textViewGetReady)
        radioFull = findViewById(R.id.radioFull)
        radioMirrorLeft = findViewById(R.id.radioMirrorLeft)
        radioMirrorRight = findViewById(R.id.radioMirrorRight)
        modeSelector = findViewById(R.id.modeSelector)
        imgCog = findViewById(R.id.imgCog)


        // Load the current user from the intent extras if available.
        val userId = intent.getIntExtra("USER_ID", 0)
        if (userId != 0) {
            // Since allowMainThreadQueries() is enabled, we can do this on the main thread.
            currentUser = AppDatabase.getDatabase(this).userDao().getUserById(userId).firstOrNull()
        }

        // Update stage duration based on the user's setting (stored in seconds).
        stageDurationMillis = (currentUser?.stageDuration ?: 10) * 1000L
        textViewTimer.text = "Time left: ${stageDurationMillis / 1000}s"

        // Initialize segmentation display from the user's setting.
        // The User field segmentationDisplay is expected to be "On" or "Off".
        segmentationEnabled = currentUser?.segmentationDisplay.equals("On", ignoreCase = true)
        segmentationOverlayView.visibility = if (segmentationEnabled) View.VISIBLE else View.GONE

        // Retrieve difficulty from the user's settings, defaulting to "Medium" if not set.
        val difficulty = currentUser?.difficulty ?: "Medium"
        textViewDifficulty.text = String.format(resources.getString(R.string.difficulty_label), difficulty)
        // Pass the difficulty setting to GameOverlayView.
        gameOverlayView.setDifficulty(difficulty)

        mediaPlayer = MediaPlayer.create(this, R.raw.game_music)
        val musicVolumeValue = (currentUser?.musicVolume ?: 50) / 100.0f
        mediaPlayer.setVolume(musicVolumeValue, musicVolumeValue)
        mediaPlayer.isLooping = true
        mediaPlayer.start()

        // --- Initialize SoundPool for short sound effects ---
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(audioAttributes)
            .build()
        soundThree = soundPool.load(this, R.raw.three, 1)
        soundTwo = soundPool.load(this, R.raw.two, 1)
        soundOne = soundPool.load(this, R.raw.one, 1)
        soundGo = soundPool.load(this, R.raw.go, 1)
        soundMiss = soundPool.load(this, R.raw.miss, 1)
        successSoundIds = listOf(
            soundPool.load(this, R.raw.terrific, 1),
            soundPool.load(this, R.raw.wow, 1),
            soundPool.load(this, R.raw.great, 1),
            soundPool.load(this, R.raw.amazing, 1)
        )
        countdownSoundIds = mapOf(
            3 to soundThree,
            2 to soundTwo,
            1 to soundOne,
            0 to soundGo
        )
        // -----------------------------------------------------

        // Set the orientation based on the user's stored setting.
        // Expected values: "Full", "Left Mirrored", "Right Mirrored"
        val orientation = currentUser?.orientation ?: "Right Mirrored"
        when (orientation) {
            "Full" -> {
                radioFull.isChecked = true
                mirrorGLSurfaceView.renderer.setMirrorMode(0)
            }
            "Left Mirrored" -> {
                radioMirrorLeft.isChecked = true
                mirrorGLSurfaceView.renderer.setMirrorMode(1)
            }
            "Right Mirrored" -> {
                radioMirrorRight.isChecked = true
                mirrorGLSurfaceView.renderer.setMirrorMode(2)
            }
            "Auto" -> {
                radioFull.visibility = View.GONE
                radioMirrorLeft.visibility = View.GONE
                radioMirrorRight.visibility = View.GONE
                modeSelector.visibility = View.GONE
                mirrorGLSurfaceView.renderer.setMirrorMode(0)
                startAutoOrientationCheck()
            }
            else -> {
                // Fallback to Full mode.
                radioFull.isChecked = true
                mirrorGLSurfaceView.renderer.setMirrorMode(0)
            }
        }
        radioFull.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) mirrorGLSurfaceView.renderer.setMirrorMode(0)
        }
        radioMirrorLeft.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) mirrorGLSurfaceView.renderer.setMirrorMode(1)
        }
        radioMirrorRight.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) mirrorGLSurfaceView.renderer.setMirrorMode(2)
        }


        imgCog.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.cog_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_restart -> {
                        restartGame()
                        true
                    }
                    R.id.menu_settings -> {
                        currentUser?.let { user ->
                            SettingsDialogFragment.newInstance(user).show(supportFragmentManager, "settingsDialog")
                        }
                        true
                    }
                    R.id.menu_quit -> {
                        finish()
                        true
                    }
                    R.id.menu_exit_game -> {
                        finishAffinity()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }

        supportFragmentManager.setFragmentResultListener("settings_changed", this) { key, bundle ->
            // Re-read the updated user from the database and update the UI.
            val userId = currentUser?.id ?: return@setFragmentResultListener
            val updatedUser = AppDatabase.getDatabase(this).userDao().getUserById(userId).firstOrNull()
            updatedUser?.let { user ->
                currentUser = user
                updateUIFromSettings(user)
            }
        }


        gameOverlayView.setOnBallTapListener { tappedColor ->
            if (tappedColor == stageTargetColor) {
                successCount++
                textViewSuccess.text = "Success: $successCount"
                // Pick a random index (0..successSoundIds.size-1) and use it for both image and sound.
                val randomIndex = (0 until successSoundIds.size).random()
                playSound(successSoundIds[randomIndex])
                gameOverlayView.showSuccessImageFromAssets(randomIndex)
            } else {
                missCount++
                textViewMiss.text = "Miss: $missCount"
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(200)
                }
                playSound(soundMiss)
            }
        }

        gameOverlayView.isSpawningBalls = false

        startPreGameCountdown()
    }

    private fun restartGame() {
        successCount = 0
        missCount = 0
        textViewSuccess.text = "Success: 0"
        textViewMiss.text = "Miss: 0"
        gameOverlayView.isSpawningBalls = false
        gameOverlayView.clearBalls()
        startPreGameCountdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
        soundPool.release()
        autoOrientationHandler.removeCallbacksAndMessages(null)
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
            }

            val preview = Preview.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setTargetRotation(rotation)
                .build()
            preview.setSurfaceProvider(mirrorGLSurfaceView.getSurfaceProvider())

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .build()
            imageAnalysis.setAnalyzer(
                segmentationExecutor,
                SelfieSegmentationAnalyzer { maskBitmap ->
                    runOnUiThread {
                        if (segmentationEnabled) {
                            segmentationOverlayView.setImageBitmap(maskBitmap)
                        }
                        gameOverlayView.updateSegmentationMask(maskBitmap)
                        latestSegmentationMask = maskBitmap
                    }
                }
            )

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun startPreGameCountdown() {
        // Clear any existing balls.
        gameOverlayView.clearBalls()
        gameOverlayView.isSpawningBalls = false

        // Determine the target color for the stage.
        stageTargetColor = fixedColors.random() // or however you choose the target.
        // Update the UI accordingly.
        setColoredTargetText(textViewTargetColor, stageTargetColor)

        // Inform GameOverlayView about the target color (and reset counters).
        gameOverlayView.setTargetColor(stageTargetColor)

        // Continue with the countdown and stage start...
        showGetReadySequence(stageTargetColor) {
            gameOverlayView.isSpawningBalls = true
            startStage()
        }
    }

    private fun showStageOverMessage(onComplete: () -> Unit) {
        textViewGetReady.apply {
            visibility = View.VISIBLE
            alpha = 0f
            text = "Stage over!"
            animate().alpha(1f).setDuration(1000).withEndAction {
                postDelayed({
                    animate().alpha(0f).setDuration(1000).withEndAction {
                        visibility = View.INVISIBLE
                        onComplete()
                    }.start()
                }, 2000)
            }.start()
        }
    }


    private fun startStage() {
        stageTimer?.cancel()
        setColoredTargetText(textViewTargetColor, stageTargetColor)
        textViewTimer.text = "Time left: ${stageDurationMillis / 1000}s"
        // Initialize remaining time with the full stage duration.
        remainingTimeMillis = stageDurationMillis
        stageTimer = object : CountDownTimer(stageDurationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMillis = millisUntilFinished  // Update remaining time.
                val secondsLeft = millisUntilFinished / 1000
                textViewTimer.text = "Time left: ${secondsLeft}s"
            }
            override fun onFinish() {
                // Set side text to indicate stage over.
                textViewTargetColor.text = "Stage over!"
                gameOverlayView.clearBalls()
                gameOverlayView.isSpawningBalls = false

                // If the current success count is higher than the stored record, update it.
                val currentRecord = currentUser?.record ?: 0
                if (successCount > currentRecord) {
                    currentUser?.let { user ->
                        val updatedUser = user.copy(record = successCount)
                        AppDatabase.getDatabase(this@MainActivity).userDao().updateUser(updatedUser)
                        currentUser = updatedUser
                    }
                }

                // Show "Stage over!" in the center with fade-in/out animation.
                showStageOverMessage {

                    handler.postDelayed({
                        startPreGameCountdown()
                    }, 0)
                }
            }

        }.start()
    }

    // --- Sound helper functions using SoundPool ---
    private fun playSound(soundId: Int) {
        // Retrieve the sounds volume (default to 50 if not set) and convert it to a 0.0–1.0 scale.
        val soundsVolumeValue = (currentUser?.soundsVolume ?: 50) / 100.0f
        soundPool.play(soundId, soundsVolumeValue, soundsVolumeValue, 1, 0, 1f)
    }

    private fun playRandomSuccessSound() {
        val randomSoundId = successSoundIds.random()
        playSound(randomSoundId)
    }

    private fun startCountdown(onCountdownEnd: () -> Unit) {
        val centerTV = textViewGetReady
        CoroutineScope(Dispatchers.Main).launch {
            for (i in 3 downTo 1) {
                centerTV.text = i.toString()
                playSound(countdownSoundIds[i]!!)
                delay(1000L)
            }
            playSound(countdownSoundIds[0]!!)
            onCountdownEnd()
        }
    }
    // ----------------------------------------------------

    private fun setColoredTargetText(tv: TextView, targetColor: Int) {
        val colorName = colorToName(targetColor)
        val fullText = "Catch the $colorName ball!"
        val spannable = SpannableString(fullText)
        val start = fullText.indexOf(colorName)
        val end = start + colorName.length
        spannable.setSpan(
            ForegroundColorSpan(targetColor),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tv.text = spannable
    }

    private fun colorToName(color: Int): String {
        return when (color) {
            Color.RED -> "RED"
            Color.BLUE -> "BLUE"
            Color.YELLOW -> "YELLOW"
            Color.GREEN -> "GREEN"
            Color.MAGENTA -> "PINK"
            else -> "UNKNOWN"
        }
    }

    private fun getColoredTargetText(targetColor: Int): SpannableString {
        val targetMessage = "Catch the ${colorToName(targetColor)} ball!"
        val spannable = SpannableString(targetMessage)
        val start = targetMessage.indexOf(colorToName(targetColor))
        val end = start + colorToName(targetColor).length
        spannable.setSpan(ForegroundColorSpan(targetColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    private fun showGetReadySequence(targetColor: Int, onSequenceEnd: () -> Unit) {
        val centerTV = textViewGetReady
        val sideTV = textViewTargetColor

        // Ensure centerTV text is centered.
        centerTV.gravity = android.view.Gravity.CENTER
        centerTV.textAlignment = View.TEXT_ALIGNMENT_CENTER

        // Phase 1: Fade in "Get Ready!" in the center.
        centerTV.animate().cancel()
        centerTV.visibility = View.VISIBLE
        centerTV.alpha = 0f
        centerTV.text = "Get Ready!"
        centerTV.animate().alpha(1f).setDuration(1000).withEndAction {
            // Hold "Get Ready!" for 1.5 seconds.
            CoroutineScope(Dispatchers.Main).launch {
                delay(1500L)
                // Phase 2: Set side text to target message.
                setColoredTargetText(sideTV, targetColor)
                // Prepare the colored target message.
                val coloredTarget = getColoredTargetText(targetColor)
                // For countdown, build a multi-line text with the target message on the first line
                // and the countdown number on the second line.
                for (i in 3 downTo 1) {
                    val builder = android.text.SpannableStringBuilder()
                    builder.append(coloredTarget)
                    builder.append("\n")
                    builder.append(i.toString())
                    centerTV.text = builder
                    playSound(countdownSoundIds[i]!!)
                    delay(1000L)
                }
                // Phase 3: Change center text to "Go!" and play the go sound.
                centerTV.text = "Go!"
                playSound(countdownSoundIds[0]!!)
                onSequenceEnd()  // Immediately start the game.
                delay(1000L)
                centerTV.animate().alpha(0f).setDuration(1000).withEndAction {
                    centerTV.visibility = View.INVISIBLE
                }.start()
            }
        }.start()
    }

    // Periodic auto-orientation check (every 1 second).
    private fun startAutoOrientationCheck() {
        autoOrientationHandler.post(object : Runnable {
            override fun run() {
                // Only run if the user's orientation is still set to Auto.
                if (currentUser?.orientation.equals("Auto", ignoreCase = true)) {
                    latestSegmentationMask?.let { mask ->
                        // Analyze the mask.
                        val (leftCount, rightCount) = analyzeMask(mask)
                        val totalLit = leftCount + rightCount
                        if (totalLit >= pixelCountThreshold) {
                            // Decide based on which half has more lit pixels.
                            if (leftCount > rightCount && currentMirrorMode != 1) {
                                mirrorGLSurfaceView.renderer.setMirrorMode(1)
                                currentMirrorMode = 1
                            } else if (rightCount > leftCount && currentMirrorMode != 2) {
                                mirrorGLSurfaceView.renderer.setMirrorMode(2)
                                currentMirrorMode = 2
                            }
                        }
                    }
                }
                // Schedule the next check after 1000ms.
                autoOrientationHandler.postDelayed(this, 1000)
            }
        })
    }

    // Helper function to analyze the segmentation mask.
    // Returns a Pair where first = lit pixel count in left half, second = lit pixel count in right half.
    private fun analyzeMask(mask: Bitmap): Pair<Int, Int> {
        val width = mask.width
        val height = mask.height
        val leftHalfWidth = width / 2

        var leftCount = 0
        var rightCount = 0

        // Create an array to hold all pixel values.
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                // We consider a pixel lit if its alpha is above 128.
                val alpha = (pixel shr 24) and 0xff
                if (alpha > 128) {
                    if (x < leftHalfWidth) {
                        leftCount++
                    } else {
                        rightCount++
                    }
                }
            }
        }
        return Pair(leftCount, rightCount)
    }

    private fun updateUIFromSettings(user: User) {
        // Update stage duration
        val newStageDurationMillis  = (user.stageDuration) * 1000L

        // If a stage is running and the remaining time is greater than the new stage duration,
        // cancel the timer and start a new one with the new duration.
        if (stageTimer != null && remainingTimeMillis > newStageDurationMillis) {
            stageTimer?.cancel()
            stageDurationMillis = newStageDurationMillis
            remainingTimeMillis = newStageDurationMillis
            stageTimer = object : CountDownTimer(newStageDurationMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    remainingTimeMillis = millisUntilFinished
                    textViewTimer.text = "Time left: ${millisUntilFinished / 1000}s"
                }
                override fun onFinish() {
                    textViewTargetColor.text = "Stage over!"
                    gameOverlayView.clearBalls()
                    gameOverlayView.isSpawningBalls = false

                    // Update record if needed.
                    val currentRecord = currentUser?.record ?: 0
                    if (successCount > currentRecord) {
                        currentUser?.let { user ->
                            val updatedUser = user.copy(record = successCount)
                            AppDatabase.getDatabase(this@MainActivity).userDao().updateUser(updatedUser)
                            currentUser = updatedUser
                        }
                    }
                    showStageOverMessage {
                        handler.postDelayed({
                            startPreGameCountdown()
                        }, 0)
                    }
                }
            }.start()
        } else {
            // If no stage is running or the remaining time is less, just update the variable.
            stageDurationMillis = newStageDurationMillis
        }

        // Update segmentation display
        segmentationEnabled = user.segmentationDisplay.equals("On", ignoreCase = true)
        segmentationOverlayView.visibility = if (segmentationEnabled) View.VISIBLE else View.GONE

        // Update difficulty text and pass new difficulty to game overlay
        textViewDifficulty.text = String.format(resources.getString(R.string.difficulty_label), user.difficulty)
        gameOverlayView.setDifficulty(user.difficulty)

        // Update music volume
        val musicVolumeValue = (user.musicVolume) / 100.0f
        mediaPlayer.setVolume(musicVolumeValue, musicVolumeValue)

        // --- Update Orientation ---
        when (user.orientation) {
            "Full" -> {
                radioFull.isChecked = true
                radioFull.visibility = View.VISIBLE
                radioMirrorLeft.visibility = View.VISIBLE
                radioMirrorRight.visibility = View.VISIBLE
                modeSelector.visibility = View.VISIBLE
                mirrorGLSurfaceView.renderer.setMirrorMode(0)
                // Stop auto orientation if running
                autoOrientationHandler.removeCallbacksAndMessages(null)
            }
            "Left Mirrored" -> {
                radioMirrorLeft.isChecked = true
                radioFull.visibility = View.VISIBLE
                radioMirrorLeft.visibility = View.VISIBLE
                radioMirrorRight.visibility = View.VISIBLE
                modeSelector.visibility = View.VISIBLE
                mirrorGLSurfaceView.renderer.setMirrorMode(1)
                autoOrientationHandler.removeCallbacksAndMessages(null)
            }
            "Right Mirrored" -> {
                radioMirrorRight.isChecked = true
                radioFull.visibility = View.VISIBLE
                radioMirrorLeft.visibility = View.VISIBLE
                radioMirrorRight.visibility = View.VISIBLE
                modeSelector.visibility = View.VISIBLE
                mirrorGLSurfaceView.renderer.setMirrorMode(2)
                autoOrientationHandler.removeCallbacksAndMessages(null)
            }
            "Auto" -> {
                // Hide radio buttons since auto mode is controlling orientation.
                radioFull.visibility = View.GONE
                radioMirrorLeft.visibility = View.GONE
                radioMirrorRight.visibility = View.GONE
                modeSelector.visibility = View.GONE
                // Optionally, reset the mirror mode to a default value until the auto check updates it.
                mirrorGLSurfaceView.renderer.setMirrorMode(0)
                // Start or restart the auto orientation check.
                startAutoOrientationCheck()
            }
        }
    }



}

