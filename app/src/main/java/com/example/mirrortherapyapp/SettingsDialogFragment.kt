package com.example.mirrortherapyapp

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment

class SettingsDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_DISABLE_OPERATION_MODE = "disable_operation_mode"

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
                putString("limb_in_focus", user.limbInFocus)
                putString ("visual_experience", user.visualExperience)
                putInt("stage_duration", user.stageDuration)
                putInt("music_volume", user.musicVolume)
                putInt("sounds_volume", user.soundsVolume)
                putInt("target_offset", user.targetOffset)

                // Pass true to disable Operation Mode in the dialog.
                putBoolean(ARG_DISABLE_OPERATION_MODE, true)
            }
            fragment.arguments = args
            return fragment
        }
    }

    // Inflate the view that includes a container for the SettingsFragment.
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate your custom layout for the dialog.
        // This layout should include your container and optionally a title and close button.
        return inflater.inflate(R.layout.dialog_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Insert the SettingsFragment into the container.
        val disable = arguments?.getBoolean(ARG_DISABLE_OPERATION_MODE, false) ?: false
        childFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsFragment.newInstance(getUserFromArguments(), disable))
            .commit()
    }

    // We do not override onCreateDialog so that the dialog automatically uses the view from onCreateView.

    private fun getUserFromArguments(): User {
        return User(
            id = arguments?.getInt("USER_ID") ?: 0,
            name = arguments?.getString("name") ?: "",
            record = arguments?.getInt("record") ?: 0,
            orientation = arguments?.getString("orientation") ?: "Right Mirrored",
            operationMode = arguments?.getString("operation_mode") ?: "Game",
            segmentationDisplay = arguments?.getString("segmentation_display") ?: "Off",
            difficulty = arguments?.getString("difficulty") ?: "Medium",
            limbInFocus = arguments?.getString("limb_in_focus") ?: "Leg",
            stageDuration = arguments?.getInt("stage_duration") ?: 20,
            musicVolume = arguments?.getInt("music_volume") ?: 50,
            soundsVolume = arguments?.getInt("sounds_volume") ?: 50,
            targetOffset = arguments?.getInt("target_offset") ?: 0,
            visualExperience = arguments?.getString("visual_experience") ?: "Standard"
        )
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Notify that settings have changed.
        val resultBundle = Bundle() // you can put updated data if needed.
        parentFragmentManager.setFragmentResult("settings_changed", resultBundle)
    }
}
