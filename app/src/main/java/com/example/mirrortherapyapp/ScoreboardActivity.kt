package com.example.mirrortherapyapp

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*

class ScoreboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ScoreboardAdapter
    private lateinit var database: AppDatabase
    private val scoreboardScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scoreboard)

        recyclerView = findViewById(R.id.recyclerViewScoreboard)
        adapter = ScoreboardAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val btnBackToMenu = findViewById<Button>(R.id.btnBackToMenu)
        btnBackToMenu.setOnClickListener {
            // Finishes this activity, returning to the main menu.
            finish()
        }

        database = AppDatabase.getDatabase(this)
        loadUsers()
    }

    private fun loadUsers() {
        scoreboardScope.launch {
            // Collect from the Flow and update the adapter.
            val users = withContext(Dispatchers.IO) {
                database.userDao().getAllUsersSortedFlow().first()
            }
            adapter.updateUsers(users)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scoreboardScope.cancel()
    }
}
