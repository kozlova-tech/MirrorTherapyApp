package com.example.mirrortherapyapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(users: List<User>): List<Long>

    @Query("SELECT * FROM users ORDER BY record DESC")
    fun getAllUsersSortedFlow(): Flow<List<User>>

    @Update
    fun updateUser(user: User)

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserById(userId: Int): List<User>

}



