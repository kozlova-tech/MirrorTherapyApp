package com.example.mirrortherapyapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private var segmentationEnabled = true

    private lateinit var mediaPlayer: MediaPlayer

    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    private val fixedColors = listOf(
        Color.RED,
        Color.BLUE,
        Color.YELLOW,
        Color.GREEN,
        Color.MAGENTA
    )

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

        mediaPlayer = MediaPlayer.create(this, R.raw.game_music)
        mediaPlayer.setVolume(0.15f, 0.15f)
        mediaPlayer.isLooping = true
        mediaPlayer.start()

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
                playRandomSuccessSound()
                gameOverlayView.showSuccessImageFromAssets()
            } else {
                missCount++
                textViewMiss.text = "Miss: $missCount"
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(200)
                }
                playMissSound()
            }
        }

        playButton.setOnClickListener { mediaPlayer.start() }
        pauseButton.setOnClickListener { mediaPlayer.pause() }

        gameOverlayView.isSpawningBalls = false

        startPreGameCountdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
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
                        // Update game overlay segmentation mask for collision detection.
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
        stageTargetColor = fixedColors.random()
        showGetReadySequence(stageTargetColor) {
            gameOverlayView.isSpawningBalls = true
            startStage()
        }
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secLeft = (millisUntilFinished / 1000).toInt()
                if (secLeft in countdownSounds.keys) {
                    playShortSound(countdownSounds[secLeft]!!)
                }
            }
            override fun onFinish() {
                playShortSound(countdownSounds[0]!!)
                gameOverlayView.isSpawningBalls = true
                startStage()
            }
        }.start()
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
                textViewTimer.text = "Stage over!"
                handler.postDelayed({
                    gameOverlayView.isSpawningBalls = false
                    startPreGameCountdown()
                }, 3000)
            }
        }.start()
    }

    private fun playShortSound(resId: Int) {
        val mp = MediaPlayer.create(this, resId)
        mp.setVolume(1.0f, 1.0f)
        mp.setOnCompletionListener { it.release() }
        mp.start()
    }

    private fun playMissSound() {
        val mp = MediaPlayer.create(this, R.raw.miss)
        mp.setVolume(0.15f, 0.15f)
        mp.start()
        mp.setOnCompletionListener { it.release() }
    }

    private fun playRandomSuccessSound() {
        val randomSoundResId = successSounds.random()
        val mp = MediaPlayer.create(this, randomSoundResId)
        mp.setVolume(1.0f, 1.0f)
        mp.start()
        mp.setOnCompletionListener { it.release() }
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

    // Get-ready sequence: center text fades in "Get Ready!" (1 sec), holds for 2 sec,
    // then fades out and updates to "Catch the [COLOR] ball!" (both center and side) and holds for 2 sec.
    private fun showGetReadySequence(targetColor: Int, onSequenceEnd: () -> Unit) {
        val centerTV = textViewGetReady
        val sideTV = textViewTargetColor

        centerTV.alpha = 0f
        centerTV.visibility = View.VISIBLE

        centerTV.text = "Get Ready!"
        centerTV.animate().alpha(1f).setDuration(1000).withEndAction {
            centerTV.postDelayed({
                centerTV.animate().alpha(0f).setDuration(1000).withEndAction {
                    setColoredTargetText(centerTV, targetColor)
                    setColoredTargetText(sideTV, targetColor)
                    centerTV.alpha = 0f
                    centerTV.animate().alpha(1f).setDuration(1000).withEndAction {
                        centerTV.postDelayed({
                            centerTV.animate().alpha(0f).setDuration(1000).withEndAction {
                                centerTV.visibility = View.INVISIBLE
                                onSequenceEnd()
                            }.start()
                        }, 2000)
                    }.start()
                }.start()
            }, 2000)
        }.start()
    }
}
