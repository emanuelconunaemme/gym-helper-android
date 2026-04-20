package com.emanuel.gymhelper.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hiit_progress",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["programId"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("programId"),
        Index(value = ["programId", "weekNumber"], unique = true)
    ]
)
data class HiitProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val hiitProgressId: Long = 0,
    val programId: Long,
    val weekNumber: Int,
    val completedCycles: Int = 0,
    val updatedAtEpochMs: Long
)
