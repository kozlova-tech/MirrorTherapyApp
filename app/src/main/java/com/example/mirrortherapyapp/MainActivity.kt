package com.example.mirrortherapyapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import android.view.Surface

class MainActivity : AppCompatActivity() {

    private lateinit var previewViewNormal: PreviewView
    private lateinit var previewViewMirror: PreviewView

    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewViewNormal = findViewById(R.id.previewViewNormal)
        previewViewMirror = findViewById(R.id.previewViewMirror)

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
            // Get the camera provider
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Get the current rotation
            val rotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display?.rotation ?: Surface.ROTATION_0
            } else {
                windowManager.defaultDisplay.rotation
            }

            // Create two Preview use cases with target rotation set in the builder
            val previewNormal = Preview.Builder()
                .setTargetRotation(rotation)
                .build()

            val previewMirror = Preview.Builder()
                .setTargetRotation(rotation)
                .build()

            // Attach the preview outputs to the PreviewViews
            previewNormal.setSurfaceProvider(previewViewNormal.surfaceProvider)
            previewMirror.setSurfaceProvider(previewViewMirror.surfaceProvider)

            try {
                // Unbind any existing use cases and bind the new ones
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, previewNormal, previewMirror)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }




    // Handle permission results
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()
            } else {
                // Inform the user that permission was not granted
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
