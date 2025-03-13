package com.example.mirrortherapyapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainMenuActivity : AppCompatActivity() {

    private lateinit var btnNewGame: Button
    private lateinit var btnSettings: Button
    private lateinit var btnScoreboard: Button
    private lateinit var btnQuit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        btnNewGame = findViewById(R.id.btnNewGame)
        btnSettings = findViewById(R.id.btnSettings)
        btnScoreboard = findViewById(R.id.btnScoreboard)
        btnQuit = findViewById(R.id.btnQuit)

        btnNewGame.setOnClickListener {
            // Start your game activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        btnSettings.setOnClickListener {
            // For now, display a message (settings not implemented)
            Toast.makeText(this, "Settings not implemented", Toast.LENGTH_SHORT).show()
        }

        btnScoreboard.setOnClickListener {
            // For now, display a message (scoreboard not implemented)
            Toast.makeText(this, "Scoreboard not implemented", Toast.LENGTH_SHORT).show()
        }

        btnQuit.setOnClickListener {
            // Exit the app
            finishAffinity()
        }
    }
}
