package com.emanuel.gymhelper.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_types")
data class ExerciseTypeEntity(
    @PrimaryKey(autoGenerate = true)
    val exerciseTypeId: Long = 0,
    val name: String,
    val youtubeVideoUrl: String? = null,
    val notes: String? = null
)
