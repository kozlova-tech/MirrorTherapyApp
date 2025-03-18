package com.example.mirrortherapyapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Size

class PlainMirrorActivity : AppCompatActivity() {

    private lateinit var mirrorGLSurfaceView: MirrorGLSurfaceView
    private lateinit var modeSelector: RadioGroup
    private lateinit var radioFull: RadioButton
    private lateinit var radioMirrorLeft: RadioButton
    private lateinit var radioMirrorRight: RadioButton

    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plain_mirror)

        // Retrieve views from XML.
        mirrorGLSurfaceView = findViewById(R.id.mirrorGLSurfaceView)
        modeSelector = findViewById(R.id.modeSelector)
        radioFull = findViewById(R.id.radioFull)
        radioMirrorLeft = findViewById(R.id.radioMirrorLeft)
        radioMirrorRight = findViewById(R.id.radioMirrorRight)

        // Set default mirror mode ("Full") and mark the corresponding radio button.
        radioFull.isChecked = true
        mirrorGLSurfaceView.renderer.setMirrorMode(0)

        // Set listeners so that when the radio buttons are selected the mirror mode changes.
        radioFull.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                mirrorGLSurfaceView.renderer.setMirrorMode(0)
            }
        }
        radioMirrorLeft.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                mirrorGLSurfaceView.renderer.setMirrorMode(1)
            }
        }
        radioMirrorRight.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                mirrorGLSurfaceView.renderer.setMirrorMode(2)
            }
        }

        // Start the camera preview once permissions are granted.
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Determine device rotation.
            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.rotation ?: Surface.ROTATION_0
            } else {
                windowManager.defaultDisplay.rotation
            }

            val preview = Preview.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setTargetRotation(rotation)
                .build()

            preview.setSurfaceProvider(mirrorGLSurfaceView.getSurfaceProvider())

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
}
