package com.emanuel.gymhelper.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emanuel.gymhelper.data.local.room.model.IntensityType

@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = TrainingEntity::class,
            parentColumns = ["trainingId"],
            childColumns = ["trainingId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseTypeEntity::class,
            parentColumns = ["exerciseTypeId"],
            childColumns = ["exerciseTypeId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index("trainingId"),
        Index("exerciseTypeId"),
        Index(value = ["trainingId", "sortOrder"], unique = true)
    ]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val exerciseId: Long = 0,
    val trainingId: Long,
    val exerciseTypeId: Long,
    val intensityType: IntensityType = IntensityType.NONE,
    val restSeconds: Int,
    val sortOrder: Int
)
