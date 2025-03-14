package com.example.mirrortherapyapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(users: List<User>): List<Long>

    @Query("SELECT * FROM users ORDER BY record DESC")
    fun getAllUsersSortedFlow(): kotlinx.coroutines.flow.Flow<List<User>>
}




