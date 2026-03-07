package com.emanuel.gymhelper.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trainings",
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
        Index(value = ["programId", "sortOrder"], unique = true)
    ]
)
data class TrainingEntity(
    @PrimaryKey(autoGenerate = true)
    val trainingId: Long = 0,
    val programId: Long,
    val name: String,
    val sortOrder: Int
)
