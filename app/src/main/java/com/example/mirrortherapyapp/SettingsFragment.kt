package com.example.mirrortherapyapp

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {

    companion object {
        private const val ARG_USER_ID = "USER_ID"
        private const val ARG_NAME = "name"
        private const val ARG_RECORD = "record"
        private const val ARG_ORIENTATION = "orientation"
        private const val ARG_OPERATION_MODE = "operation_mode"
        private const val ARG_SEGMENTATION_DISPLAY = "segmentation_display"
        private const val ARG_DIFFICULTY = "difficulty"
        private const val ARG_STAGE_DURATION = "stage_duration"
        private const val ARG_MUSIC_VOLUME = "music_volume"
        private const val ARG_SOUNDS_VOLUME = "sounds_volume"

        fun newInstance(user: User): SettingsFragment {
            val fragment = SettingsFragment()
            val args = Bundle().apply {
                putInt(ARG_USER_ID, user.id)
                putString(ARG_NAME, user.name)
                putInt(ARG_RECORD, user.record)
                putString(ARG_ORIENTATION, user.orientation)
                putString(ARG_OPERATION_MODE, user.operationMode)
                putString(ARG_SEGMENTATION_DISPLAY, user.segmentationDisplay)
                putString(ARG_DIFFICULTY, user.difficulty)
                putInt(ARG_STAGE_DURATION, user.stageDuration)
                putInt(ARG_MUSIC_VOLUME, user.musicVolume)
                putInt(ARG_SOUNDS_VOLUME, user.soundsVolume)
            }
            fragment.arguments = args
            return fragment
        }
    }



    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // First, create a User object from the passed arguments.
        val userId = arguments?.getInt(ARG_USER_ID) ?: 0
        val name = arguments?.getString(ARG_NAME) ?: ""
        val record = arguments?.getInt(ARG_RECORD) ?: 0
        val orientation = arguments?.getString(ARG_ORIENTATION) ?: "Right Mirrored"
        val operationMode = arguments?.getString(ARG_OPERATION_MODE) ?: "Game"
        val segmentationDisplay = arguments?.getString(ARG_SEGMENTATION_DISPLAY) ?: "Off"
        val difficulty = arguments?.getString(ARG_DIFFICULTY) ?: "Medium"
        val stageDuration = arguments?.getInt(ARG_STAGE_DURATION) ?: 10
        val musicVolume = arguments?.getInt(ARG_MUSIC_VOLUME) ?: 50
        val soundsVolume = arguments?.getInt(ARG_SOUNDS_VOLUME) ?: 50
        val currentUser = User(
            id = userId,
            name = name,
            record = record,
            orientation = orientation,
            operationMode = operationMode,
            segmentationDisplay = segmentationDisplay,
            difficulty = difficulty,
            stageDuration = stageDuration,
            musicVolume = musicVolume,
            soundsVolume = soundsVolume
        )

        // Set the custom PreferenceDataStore using the current user.
        preferenceManager.preferenceDataStore = UserSettingsDataStore(
            AppDatabase.getDatabase(requireContext()).userDao(),
            userId,
            currentUser
        )
        // Now load the preferences from XML.
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

}

