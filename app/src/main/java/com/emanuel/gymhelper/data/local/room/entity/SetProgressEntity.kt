package com.emanuel.gymhelper.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emanuel.gymhelper.data.local.room.progress.ProgressStatus

@Entity(
    tableName = "set_progress",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseProgressEntity::class,
            parentColumns = ["exerciseProgressId"],
            childColumns = ["exerciseProgressId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("exerciseProgressId"),
        Index(value = ["exerciseProgressId", "setIndex"], unique = true)
    ]
)
data class SetProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val setProgressId: Long = 0,
    val exerciseProgressId: Long,
    val setIndex: Int,
    val status: String = ProgressStatus.PENDING,
    val updatedAtEpochMs: Long
)
