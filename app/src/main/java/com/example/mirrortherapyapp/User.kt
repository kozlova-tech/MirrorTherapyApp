package com.example.mirrortherapyapp

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "record")
    val record: Int,
    @ColumnInfo(name = "orientation")
    val orientation: String = "Right Mirrored",
    @ColumnInfo(name = "operation_mode")
    val operationMode: String = "Game",
    @ColumnInfo(name = "segmentation_display")
    val segmentationDisplay: String = "Off",
    @ColumnInfo(name = "difficulty")
    val difficulty: String = "Medium",
    @ColumnInfo(name = "stage_duration")
    val stageDuration: Int = 10,
    @ColumnInfo(name = "music_volume")
    val musicVolume: Int = 50,
    @ColumnInfo(name = "sounds_volume")
    val soundsVolume: Int = 50
) : Parcelable
