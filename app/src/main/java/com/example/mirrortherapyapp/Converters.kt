package com.example.mirrortherapyapp

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromIntToBoolean(value: Int): Boolean = value != 0

    @TypeConverter
    fun fromBooleanToInt(value: Boolean): Int = if (value) 1 else 0
}
