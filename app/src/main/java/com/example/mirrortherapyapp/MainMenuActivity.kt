package com.example.mirrortherapyapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull


class MainMenuActivity : AppCompatActivity() {

    private lateinit var btnNewGame: Button
    private lateinit var btnSettings: Button
    private lateinit var btnScoreboard: Button
    private lateinit var btnQuit: Button
    private lateinit var spinnerUser: Spinner
    private lateinit var userDao: UserDao

    private var userList: List<User> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        btnNewGame = findViewById(R.id.btnNewGame)
        btnSettings = findViewById(R.id.btnSettings)
        btnScoreboard = findViewById(R.id.btnScoreboard)
        btnQuit = findViewById(R.id.btnQuit)

        spinnerUser = findViewById(R.id.spinnerUser)
        userDao = AppDatabase.getDatabase(this).userDao()
        loadUsersIntoSpinner() // Load the current list of users into the spinner.

        btnNewGame.setOnClickListener {
            // Start your game activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        btnSettings.setOnClickListener {
            // Get the selected spinner position.
            val pos = spinnerUser.selectedItemPosition
            // If "Add User..." is selected (position 0), show a message or skip.
            if (pos == 0) {
                Toast.makeText(this, "Please select a valid user", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // The list stored in userList is aligned such that userList[0] corresponds to spinner position 1.
            val selectedUserId = userList[pos - 1].id
            val intent = Intent(this, SettingsActivity::class.java)
            intent.putExtra("USER_ID", selectedUserId)
            startActivity(intent)
        }

        btnScoreboard.setOnClickListener {
            startActivity(Intent(this, ScoreboardActivity::class.java))
        }

        btnQuit.setOnClickListener {
            // Exit the app
            finishAffinity()
        }

        spinnerUser.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position == 0) {  // "Add User..." selected.
                    val input = EditText(this@MainMenuActivity)
                    AlertDialog.Builder(this@MainMenuActivity)
                        .setTitle("Add User")
                        .setMessage("Enter new user name:")
                        .setView(input)
                        .setPositiveButton("OK") { _, _ ->
                            val newUserName = input.text.toString().trim()
                            if (newUserName.isNotEmpty()) {
                                val db = AppDatabase.getDatabase(this@MainMenuActivity)
                                val userDao = db.userDao()
                                CoroutineScope(Dispatchers.IO).launch {
                                    userDao.insertAll(listOf(User(name = newUserName, record = 0)))
                                    // Small delay to ensure the DB updates.
                                    delay(500)
                                    withContext(Dispatchers.Main) {
                                        loadUsersIntoSpinner {
                                            val adapter = spinnerUser.adapter as ArrayAdapter<String>
                                            val pos = adapter.getPosition(newUserName)
                                            if (pos >= 0) spinnerUser.setSelection(pos)
                                        }
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) { }
        }



    }

    private fun loadUsersIntoSpinner(onLoaded: (() -> Unit)? = null) {
        val db = AppDatabase.getDatabase(this)
        val userDao = db.userDao()
        CoroutineScope(Dispatchers.Main).launch {
            val users = userDao.getAllUsersSortedFlow().firstOrNull() ?: emptyList()
            // Save the list for later retrieval of user IDs.
            userList = users

            // Build a list of names with the first item as "Add User..."
            val userNames = mutableListOf("Add User...")
            userNames.addAll(users.map { it.name })
            val adapter = ArrayAdapter(this@MainMenuActivity, R.layout.spinner_item, userNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerUser.adapter = adapter

            // Set default selection to the first real user if available.
            if (adapter.count > 1 && spinnerUser.selectedItemPosition == 0) {
                spinnerUser.setSelection(1)
            }
            onLoaded?.invoke()
        }
    }





}
