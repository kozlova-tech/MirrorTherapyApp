package com.example.mirrortherapyapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Size

class PlainMirrorActivity : AppCompatActivity() {

    private lateinit var mirrorGLSurfaceView: MirrorGLSurfaceView
    private lateinit var imgCog: ImageView

    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plain_mirror)

        mirrorGLSurfaceView = findViewById(R.id.mirrorGLSurfaceView)
        imgCog = findViewById(R.id.imgCog)

        // Set up the cog icon to display a popup menu.
        imgCog.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.cog_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_settings -> {
                        // Launch the settings activity.
                        val intent = Intent(this, SettingsActivity::class.java)
                        // Optionally pass the user ID if needed.
                        intent.putExtra("USER_ID", intent.getIntExtra("USER_ID", 0))
                        startActivity(intent)
                        true
                    }
                    R.id.menu_quit -> {
                        finishAffinity()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // Check for camera permission and start the camera preview.
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

    // Optionally override onRequestPermissionsResult if needed.
}
