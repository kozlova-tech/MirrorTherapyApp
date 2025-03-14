package com.example.mirrortherapyapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [User::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addCallback(AppDatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class AppDatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        database.userDao().insertAll(
                            listOf(
                                User(name = "Alex", record = 123),
                                User(name = "Lidor", record = 456),
                                User(name = "Mor", record = 789),
                                User(name = "Johanna", record = 234),
                                User(name = "Elisha", record = 567),
                                User(name = "Nechama", record = 600),
                                User(name = "Ofir", record = 430)
                            )
                        )
                    }
                }
            }
        }





    }
}
