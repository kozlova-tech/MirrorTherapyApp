package com.example.mirrortherapyapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment

class SettingsDialogFragment : DialogFragment() {

    companion object {
        fun newInstance(user: User): SettingsDialogFragment {
            val fragment = SettingsDialogFragment()
            val args = Bundle().apply {
                putInt("USER_ID", user.id)
                putString("name", user.name)
                putInt("record", user.record)
                putString("orientation", user.orientation)
                putString("operation_mode", user.operationMode)
                putString("segmentation_display", user.segmentationDisplay)
                putString("difficulty", user.difficulty)
                putInt("stage_duration", user.stageDuration)
                putInt("music_volume", user.musicVolume)
                putInt("sounds_volume", user.soundsVolume)
            }
            fragment.arguments = args
            return fragment
        }
    }

    // Inflate the view containing the container.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_settings, container, false)
    }

    // Once the view is created, insert the SettingsFragment.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Retrieve the user ID from arguments.
        val userId = arguments?.getInt("USER_ID") ?: 0
        if (userId != 0) {
            // Re-read the updated user from the database.
            val updatedUser = AppDatabase.getDatabase(requireContext())
                .userDao()
                .getUserById(userId)
                .firstOrNull()
            updatedUser?.let {
                // Replace the container with a new instance of SettingsFragment using the updated user.
                childFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, SettingsFragment.newInstance(it))
                    .commit()
            }
        }
    }


    // Do not override onCreateDialog so that the view from onCreateView is used.

    private fun getUserFromArguments(): User {
        return User(
            id = arguments?.getInt("USER_ID") ?: 0,
            name = arguments?.getString("name") ?: "",
            record = arguments?.getInt("record") ?: 0,
            orientation = arguments?.getString("orientation") ?: "Right Mirrored",
            operationMode = arguments?.getString("operation_mode") ?: "Game",
            segmentationDisplay = arguments?.getString("segmentation_display") ?: "Off",
            difficulty = arguments?.getString("difficulty") ?: "Medium",
            stageDuration = arguments?.getInt("stage_duration") ?: 10,
            musicVolume = arguments?.getInt("music_volume") ?: 50,
            soundsVolume = arguments?.getInt("sounds_volume") ?: 50
        )
    }
}
