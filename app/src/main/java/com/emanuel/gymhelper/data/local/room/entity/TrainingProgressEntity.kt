package com.emanuel.gymhelper.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emanuel.gymhelper.data.local.room.progress.ProgressStatus

@Entity(
    tableName = "training_progress",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["programId"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrainingEntity::class,
            parentColumns = ["trainingId"],
            childColumns = ["trainingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("programId"),
        Index("trainingId"),
        Index(value = ["trainingId", "weekNumber"], unique = true)
    ]
)
data class TrainingProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val trainingProgressId: Long = 0,
    val programId: Long,
    val trainingId: Long,
    val weekNumber: Int,
    val status: String = ProgressStatus.PENDING,
    val updatedAtEpochMs: Long
)
