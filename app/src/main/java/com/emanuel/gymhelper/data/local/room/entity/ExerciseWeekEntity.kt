package com.emanuel.gymhelper.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_weeks",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("exerciseId"),
        Index(value = ["exerciseId", "weekNumber"], unique = true)
    ]
)
data class ExerciseWeekEntity(
    @PrimaryKey(autoGenerate = true)
    val exerciseWeekId: Long = 0,
    val exerciseId: Long,
    val weekNumber: Int
)
