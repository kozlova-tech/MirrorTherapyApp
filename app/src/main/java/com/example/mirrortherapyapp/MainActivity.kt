package com.example.mirrortherapyapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
    private lateinit var textViewSuccess: TextView
    private lateinit var textViewMiss: TextView
    private lateinit var textViewGetReady: TextView

    private lateinit var radioFull: RadioButton
    private lateinit var radioMirrorLeft: RadioButton
    private lateinit var radioMirrorRight: RadioButton
    private lateinit var modeSelector: RadioGroup

    private lateinit var playButton: Button
    private lateinit var pauseButton: Button
    private lateinit var toggleSegmentationButton: Button

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
    private val stageDurationMillis = 10_000L
    private var stageTimer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val segmentationExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mirrorGLSurfaceView = findViewById(R.id.mirrorGLSurfaceView)
        gameOverlayView = findViewById(R.id.gameOverlayView)
        segmentationOverlayView = findViewById(R.id.segmentationOverlayView)
        textViewTargetColor = findViewById(R.id.textViewTargetColor)
        textViewTimer = findViewById(R.id.textViewTimer)
        textViewSuccess = findViewById(R.id.textViewSuccess)
        textViewMiss = findViewById(R.id.textViewMiss)
        textViewGetReady = findViewById(R.id.textViewGetReady)
        radioFull = findViewById(R.id.radioFull)
        radioMirrorLeft = findViewById(R.id.radioMirrorLeft)
        radioMirrorRight = findViewById(R.id.radioMirrorRight)
        modeSelector = findViewById(R.id.modeSelector)
        playButton = findViewById(R.id.btnPlay)
        pauseButton = findViewById(R.id.btnPause)
        toggleSegmentationButton = findViewById(R.id.btnToggleSegmentation)
        imgCog = findViewById(R.id.imgCog)

        mediaPlayer = MediaPlayer.create(this, R.raw.game_music)
        mediaPlayer.setVolume(0.15f, 0.15f)
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

        radioFull.isChecked = true
        mirrorGLSurfaceView.renderer.setMirrorMode(0)
        radioFull.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) mirrorGLSurfaceView.renderer.setMirrorMode(0)
        }
        radioMirrorLeft.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) mirrorGLSurfaceView.renderer.setMirrorMode(1)
        }
        radioMirrorRight.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) mirrorGLSurfaceView.renderer.setMirrorMode(2)
        }

        toggleSegmentationButton.text = "Disable Segmentation"
        toggleSegmentationButton.setOnClickListener {
            segmentationEnabled = !segmentationEnabled
            if (segmentationEnabled) {
                segmentationOverlayView.visibility = View.VISIBLE
                toggleSegmentationButton.text = "Disable Segmentation"
            } else {
                segmentationOverlayView.visibility = View.GONE
                toggleSegmentationButton.text = "Enable Segmentation"
            }
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
                        android.widget.Toast.makeText(this, "Settings not implemented", android.widget.Toast.LENGTH_SHORT).show()
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

        playButton.setOnClickListener { mediaPlayer.start() }
        pauseButton.setOnClickListener { mediaPlayer.pause() }

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
        gameOverlayView.clearBalls()
        gameOverlayView.isSpawningBalls = false
        stageTargetColor = fixedColors.random()
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
        textViewTimer.text = "Time left: 10s"
        stageTimer = object : CountDownTimer(stageDurationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                textViewTimer.text = "Time left: ${secondsLeft}s"
            }
            override fun onFinish() {
                // Set side text to indicate stage over.
                textViewTargetColor.text = "Stage over!"

                gameOverlayView.clearBalls()
                gameOverlayView.isSpawningBalls = false
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
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
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




}
