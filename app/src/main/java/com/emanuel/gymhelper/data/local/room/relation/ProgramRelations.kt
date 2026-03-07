package com.emanuel.gymhelper.data.local.room.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.emanuel.gymhelper.data.local.room.entity.ExerciseEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseTypeEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseWeekEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseWeekSetEntity
import com.emanuel.gymhelper.data.local.room.entity.ProgramEntity
import com.emanuel.gymhelper.data.local.room.entity.TrainingEntity

data class ProgramWithTrainings(
    @Embedded
    val program: ProgramEntity,
    @Relation(
        parentColumn = "programId",
        entityColumn = "programId",
        entity = TrainingEntity::class
    )
    val trainings: List<TrainingWithExercises>
)

data class TrainingWithExercises(
    @Embedded
    val training: TrainingEntity,
    @Relation(
        parentColumn = "trainingId",
        entityColumn = "trainingId",
        entity = ExerciseEntity::class
    )
    val exercises: List<ExerciseWithDetails>
)

data class ExerciseWithDetails(
    @Embedded
    val exercise: ExerciseEntity,
    @Relation(
        parentColumn = "exerciseTypeId",
        entityColumn = "exerciseTypeId"
    )
    val exerciseType: ExerciseTypeEntity,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "exerciseId",
        entity = ExerciseWeekEntity::class
    )
    val weeks: List<ExerciseWeekWithSets>
)

data class ExerciseWeekWithSets(
    @Embedded
    val week: ExerciseWeekEntity,
    @Relation(
        parentColumn = "exerciseWeekId",
        entityColumn = "exerciseWeekId"
    )
    val setRepetitions: List<ExerciseWeekSetEntity>
)
