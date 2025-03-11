package com.example.mirrortherapyapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Surface
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    private lateinit var previewViewLeft: PreviewView
    private lateinit var previewViewRight: PreviewView
    private lateinit var radioMirrorLeft: RadioButton
    private lateinit var radioMirrorRight: RadioButton

    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewViewLeft = findViewById(R.id.previewViewLeft)
        previewViewRight = findViewById(R.id.previewViewRight)
        radioMirrorLeft = findViewById(R.id.radioMirrorLeft)
        radioMirrorRight = findViewById(R.id.radioMirrorRight)

        // Set "Mirror Left" as default
        radioMirrorLeft.isChecked = true
        previewViewLeft.scaleX = -1f
        previewViewRight.scaleX = 1f

        radioMirrorLeft.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                previewViewLeft.scaleX = -1f
                previewViewRight.scaleX = 1f
            }
        }

        radioMirrorRight.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                previewViewRight.scaleX = -1f
                previewViewLeft.scaleX = 1f
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
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val rotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display?.rotation ?: Surface.ROTATION_0
            } else {
                windowManager.defaultDisplay.rotation
            }

            val previewLeft = Preview.Builder().setTargetRotation(rotation).build()
            val previewRight = Preview.Builder()
                .setTargetRotation(rotation)
                .build()

            previewViewLeft.scaleX = if (radioMirrorLeft.isChecked) -1f else 1f
            previewViewRight.scaleX = if (radioMirrorRight.isChecked) -1f else 1f

            previewViewLeft.surfaceProvider?.let { previewLeftProvider ->
                previewLeft.setSurfaceProvider(previewLeftProvider)
            }

            previewViewRight.surfaceProvider?.let { previewRightProvider ->
                previewRight.setSurfaceProvider(previewRightProvider)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, previewLeft, previewRight)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateMirroring() {
        if (radioMirrorLeft.isChecked) {
            previewViewLeft.scaleX = -1f
            previewViewRight.scaleX = 1f
        } else if (radioMirrorRight.isChecked) {
            previewViewRight.scaleX = -1f
            previewViewLeft.scaleX = 1f
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}