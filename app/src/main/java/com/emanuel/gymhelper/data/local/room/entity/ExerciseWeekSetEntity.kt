package com.emanuel.gymhelper.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_week_sets",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseWeekEntity::class,
            parentColumns = ["exerciseWeekId"],
            childColumns = ["exerciseWeekId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("exerciseWeekId"),
        Index(value = ["exerciseWeekId", "sortOrder"], unique = true)
    ]
)
data class ExerciseWeekSetEntity(
    @PrimaryKey(autoGenerate = true)
    val exerciseWeekSetId: Long = 0,
    val exerciseWeekId: Long,
    val sortOrder: Int,
    val setCount: Int,
    val repetitionsMin: Int,
    val repetitionsMax: Int
)
