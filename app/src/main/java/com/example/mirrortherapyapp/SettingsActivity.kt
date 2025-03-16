package com.example.mirrortherapyapp

import android.os.Bundle
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Retrieve the user ID from the intent extras.
        val userId = intent.getIntExtra("USER_ID", 0)
        val userDao = AppDatabase.getDatabase(this).userDao()

        // Launch a coroutine to load the user from Room.
        lifecycleScope.launch {
            // getUserById returns a List<User>; take the first (if exists).
            val userList = userDao.getUserById(userId)
            val currentUser = userList.firstOrNull()
            if (currentUser != null) {
                // Pass the User object to SettingsFragment using a Bundle.
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, SettingsFragment.newInstance(currentUser))
                    .commit()
            } else {
                // Handle case when no user was found (e.g. show an error)
                Toast.makeText(this@SettingsActivity, "User not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

