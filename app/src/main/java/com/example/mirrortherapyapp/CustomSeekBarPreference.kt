package com.example.mirrortherapyapp

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import androidx.preference.R

class CustomSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.seekBarPreferenceStyle,
    defStyleRes: Int = 0
) : SeekBarPreference(context, attrs, defStyleAttr, defStyleRes) {

    // Retrieve the effective minimum from XML.
    private var effectiveMin: Int = 0

    init {
        // Look up the "android:min" attribute.
        attrs?.let {
            val a = context.obtainStyledAttributes(it, intArrayOf(android.R.attr.min))
            effectiveMin = a.getInt(0, 0) // default to 0 if not set
            a.recycle()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // Ensure the summary TextView is present.
        val summaryView = holder.findViewById(android.R.id.summary) as? TextView
        summaryView?.text = "${getValue()} seconds"

        // Get the SeekBar widget from the layout.
        val seekBar = holder.findViewById(androidx.preference.R.id.seekbar) as? SeekBar
        if (seekBar != null) {
            // The actual maximum value is defined in XML (e.g. 30).
            val actualMax = getMax()
            // Adjust the SeekBar's max to reflect an offset range.
            val displayMax = actualMax - effectiveMin
            seekBar.max = displayMax

            // Set the SeekBar's progress as the stored value minus the effective min.
            seekBar.progress = getValue() - effectiveMin

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    // Convert the SeekBar progress to the actual value.
                    val actualValue = progress + effectiveMin
                    summaryView?.text = "$actualValue seconds"
                    if (fromUser) {
                        setValue(actualValue)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) { }
                override fun onStopTrackingTouch(sb: SeekBar?) { }
            })
        }
    }
}
