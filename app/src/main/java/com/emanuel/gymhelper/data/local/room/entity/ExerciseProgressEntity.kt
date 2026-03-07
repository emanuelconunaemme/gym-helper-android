package com.emanuel.gymhelper.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emanuel.gymhelper.data.local.room.progress.ProgressStatus

@Entity(
    tableName = "exercise_progress",
    foreignKeys = [
        ForeignKey(
            entity = TrainingProgressEntity::class,
            parentColumns = ["trainingProgressId"],
            childColumns = ["trainingProgressId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("trainingProgressId"),
        Index("exerciseId"),
        Index(value = ["trainingProgressId", "exerciseId"], unique = true)
    ]
)
data class ExerciseProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val exerciseProgressId: Long = 0,
    val trainingProgressId: Long,
    val exerciseId: Long,
    val weekNumber: Int,
    val plannedSets: Int,
    val completedSets: Int = 0,
    val skippedSets: Int = 0,
    val status: String = ProgressStatus.PENDING,
    val weightText: String? = null,
    val completedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long
)
