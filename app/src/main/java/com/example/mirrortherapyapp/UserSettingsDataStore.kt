package com.example.mirrortherapyapp

import androidx.preference.PreferenceDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A PreferenceDataStore that reads and writes settings from the User entity.
 * Settings include:
 *  - "orientation": String
 *  - "operation_mode": String
 *  - "difficulty": String
 *  - "segmentation_display": Boolean
 *  - "stage_duration": Int
 *  - "music_volume": Int
 *  - "sounds_volume": Int
 *
 * When a value is changed, this class updates the cached User object and writes it
 * to the Room database via the UserDao.
 */
class UserSettingsDataStore(
    private val userDao: UserDao,
    private val userId: Int,
    // The current User object for which settings are managed.
    private var user: User
) : PreferenceDataStore() {

    // Return string values from the cached user.
    override fun getString(key: String?, defValue: String?): String? {
        return when (key) {
            "orientation" -> user.orientation
            "operation_mode" -> user.operationMode
            "difficulty" -> user.difficulty
            "segmentation_display" -> user.segmentationDisplay
            else -> defValue
        }
    }

    // Update string values in the cached user and save.
    override fun putString(key: String?, value: String?) {
        if (key == null || value == null) return
        user = when (key) {
            "orientation" -> user.copy(orientation = value)
            "operation_mode" -> user.copy(operationMode = value)
            "difficulty" -> user.copy(difficulty = value)
            "segmentation_display" -> user.copy(segmentationDisplay = value)
            else -> user
        }
        saveUser()
    }

    // Return integer values from the cached user.
    override fun getInt(key: String?, defValue: Int): Int {
        return when (key) {
            "stage_duration" -> user.stageDuration
            "music_volume" -> user.musicVolume
            "sounds_volume" -> user.soundsVolume
            "target_offset" -> user.targetOffset
            else -> defValue
        }
    }

    // Update integer values in the cached user and save.
    override fun putInt(key: String?, value: Int) {
        if (key == null) return
        user = when (key) {
            "stage_duration" -> user.copy(stageDuration = value)
            "music_volume" -> user.copy(musicVolume = value)
            "sounds_volume" -> user.copy(soundsVolume = value)
            "target_offset" -> user.copy(targetOffset = value)
            else -> user
        }
        saveUser()
    }

    // Save the updated user to the database on a background thread.
    private fun saveUser() {
        CoroutineScope(Dispatchers.IO).launch {
            userDao.updateUser(user)
        }
    }
}
