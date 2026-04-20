package com.emanuel.gymhelper.data.importer

import androidx.room.withTransaction
import com.emanuel.gymhelper.data.local.room.GymHelperDatabase
import com.emanuel.gymhelper.data.local.room.entity.ExerciseEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseTypeEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseWeekEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseWeekSetEntity
import com.emanuel.gymhelper.data.local.room.entity.ProgramEntity
import com.emanuel.gymhelper.data.local.room.entity.TrainingEntity
import com.emanuel.gymhelper.data.local.room.model.IntensityType

class ProgramJsonImporter(
    private val database: GymHelperDatabase
) {

    suspend fun import(json: String): Long {
        val payload = ProgramJsonParser.parse(json)
        val dao = database.programDao()

        return database.withTransaction {
            val programId = dao.insertProgram(
                ProgramEntity(
                    name = payload.program.name,
                    prehabMarkdown = payload.program.prehabMarkdown,
                    numberOfWeeks = payload.program.numberOfWeeks,
                    unloadWeek = payload.program.unloadWeek
                )
            )

            val exerciseTypeIdByKey = mutableMapOf<ExerciseTypeKey, Long>()

            payload.program.trainings.forEachIndexed { trainingIndex, trainingImport ->
                val trainingId = dao.insertTrainings(
                    listOf(
                        TrainingEntity(
                            programId = programId,
                            name = trainingImport.name,
                            sortOrder = trainingIndex
                        )
                    )
                ).first()

                trainingImport.exercises.forEachIndexed { exerciseIndex, exerciseImport ->
                    val typeKey = ExerciseTypeKey(
                        name = exerciseImport.exerciseType.name,
                        youtubeVideo = exerciseImport.exerciseType.youtubeVideo,
                        notes = exerciseImport.exerciseType.notes
                    )
                    val exerciseTypeId = exerciseTypeIdByKey.getOrPut(typeKey) {
                        dao.insertExerciseTypes(
                            listOf(
                                ExerciseTypeEntity(
                                    name = exerciseImport.exerciseType.name,
                                    youtubeVideoUrl = exerciseImport.exerciseType.youtubeVideo,
                                    notes = exerciseImport.exerciseType.notes
                                )
                            )
                        ).first()
                    }

                    val intensityType = IntensityType.fromDbValue(exerciseImport.intensityType)
                    val exerciseId = dao.insertExercises(
                        listOf(
                            ExerciseEntity(
                                trainingId = trainingId,
                                exerciseTypeId = exerciseTypeId,
                                intensityType = intensityType,
                                restSeconds = exerciseImport.restSeconds,
                                sortOrder = exerciseIndex
                            )
                        )
                    ).first()

                    exerciseImport.weeks
                        .sortedBy { it.week }
                        .forEach { weekImport ->
                            val weekId = dao.insertExerciseWeeks(
                                listOf(
                                    ExerciseWeekEntity(
                                        exerciseId = exerciseId,
                                        weekNumber = weekImport.week
                                    )
                                )
                            ).first()

                            val sets = weekImport.setRepetitions.mapIndexed { setIndex, setImport ->
                                ExerciseWeekSetEntity(
                                    exerciseWeekId = weekId,
                                    sortOrder = setIndex,
                                    setCount = setImport.setCount,
                                    repetitionsMin = setImport.repetitionsMin,
                                    repetitionsMax = setImport.repetitionsMax
                                )
                            }
                            if (sets.isNotEmpty()) {
                                dao.insertExerciseWeekSets(sets)
                            }
                        }
                }
            }

            programId
        }
    }
}

private data class ExerciseTypeKey(
    val name: String,
    val youtubeVideo: String?,
    val notes: String?
)
