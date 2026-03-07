package com.emanuel.gymhelper.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "program_progress",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["programId"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["programId"], unique = true)
    ]
)
data class ProgramProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val programProgressId: Long = 0,
    val programId: Long,
    val currentWeek: Int = 1,
    val updatedAtEpochMs: Long
)
