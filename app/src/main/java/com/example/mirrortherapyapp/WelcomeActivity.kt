package com.example.mirrortherapyapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    private val delayMillis = 5000L
    private val handler = Handler(Looper.getMainLooper())
    private val proceedRunnable = Runnable { proceedToMenu() }
    private var hasProceeded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        handler.postDelayed(proceedRunnable, delayMillis)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If user taps, proceed immediately.
        if (!hasProceeded) {
            handler.removeCallbacks(proceedRunnable)
            proceedToMenu()
        }
        return true
    }

    private fun proceedToMenu() {
        if (hasProceeded) return
        hasProceeded = true
        startActivity(Intent(this, MainMenuActivity::class.java))
        finish()
    }
}
