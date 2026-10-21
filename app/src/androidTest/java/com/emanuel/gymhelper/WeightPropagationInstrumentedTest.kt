package com.emanuel.gymhelper

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emanuel.gymhelper.data.local.room.GymHelperDatabase
import com.emanuel.gymhelper.data.local.room.entity.ExerciseEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseTypeEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseWeekEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseWeekSetEntity
import com.emanuel.gymhelper.data.local.room.entity.ProgramEntity
import com.emanuel.gymhelper.data.local.room.entity.TrainingEntity
import com.emanuel.gymhelper.data.local.room.entity.TrainingProgressEntity
import com.emanuel.gymhelper.data.tracker.WorkoutTrackerRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeightPropagationInstrumentedTest {

    private lateinit var database: GymHelperDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GymHelperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savedWeightSeedsNewWeeksAndUpdatesExistingUntouchedWeeks() = runBlocking {
        val programDao = database.programDao()
        val progressDao = database.workoutProgressDao()
        val repository = WorkoutTrackerRepository(database)

        val exerciseTypeId = programDao.insertExerciseTypes(
            listOf(ExerciseTypeEntity(name = "Bench press"))
        ).single()

        val sourceProgramId = programDao.insertProgram(
            ProgramEntity(
                name = "EMANUEL MAZZILLI 8",
                prehabMarkdown = "",
                numberOfWeeks = 4,
                unloadWeek = true
            )
        )
        val sourceTrainingId = programDao.insertTrainings(
            listOf(TrainingEntity(programId = sourceProgramId, name = "Push", sortOrder = 1))
        ).single()
        val sourceExerciseId = programDao.insertExercises(
            listOf(
                ExerciseEntity(
                    trainingId = sourceTrainingId,
                    exerciseTypeId = exerciseTypeId,
                    restSeconds = 60,
                    sortOrder = 1
                )
            )
        ).single()
        val sourceTrainingProgressId = progressDao.insertTrainingProgress(
            TrainingProgressEntity(
                programId = sourceProgramId,
                trainingId = sourceTrainingId,
                weekNumber = 4,
                updatedAtEpochMs = 1
            )
        )
        progressDao.insertExerciseProgress(
            ExerciseProgressEntity(
                trainingProgressId = sourceTrainingProgressId,
                exerciseId = sourceExerciseId,
                weekNumber = 4,
                plannedSets = 1,
                weightText = "80",
                updatedAtEpochMs = 1
            )
        )

        val targetProgramId = programDao.insertProgram(
            ProgramEntity(
                name = "EMANUEL MAZZILLI 9",
                prehabMarkdown = "",
                numberOfWeeks = 4,
                unloadWeek = true
            )
        )
        val targetTrainingId = programDao.insertTrainings(
            listOf(TrainingEntity(programId = targetProgramId, name = "Push", sortOrder = 1))
        ).single()
        val targetExerciseId = programDao.insertExercises(
            listOf(
                ExerciseEntity(
                    trainingId = targetTrainingId,
                    exerciseTypeId = exerciseTypeId,
                    restSeconds = 60,
                    sortOrder = 1
                )
            )
        ).single()
        val exerciseWeekIds = programDao.insertExerciseWeeks(
            (1..4).map { week ->
                ExerciseWeekEntity(exerciseId = targetExerciseId, weekNumber = week)
            }
        )
        programDao.insertExerciseWeekSets(
            exerciseWeekIds.map { exerciseWeekId ->
                ExerciseWeekSetEntity(
                    exerciseWeekId = exerciseWeekId,
                    sortOrder = 1,
                    setCount = 1,
                    repetitionsMin = 8,
                    repetitionsMax = 8
                )
            }
        )

        val training = programDao.getProgramById(targetProgramId)!!.trainings.single()
        val week1 = repository.loadTrainingState(targetProgramId, training, 1, 4)
        val week1Progress = week1.exerciseStates.single().progress
        assertEquals("80", week1Progress.weightText)

        val week3 = repository.loadTrainingState(targetProgramId, training, 3, 4)
        val week3ProgressId = week3.exerciseStates.single().progress.exerciseProgressId
        assertEquals("80", week3.exerciseStates.single().progress.weightText)

        // Simulate a weight edit made before propagation was fixed. Reloading the untouched
        // future week repairs the stale old-program carryover already stored there.
        progressDao.updateExerciseWeight(week1Progress.exerciseProgressId, "90", 2)
        val repairedWeek3 = repository.loadTrainingState(targetProgramId, training, 3, 4)
        assertEquals("90", repairedWeek3.exerciseStates.single().progress.weightText)

        repository.saveWeight(week1Progress.exerciseProgressId, " 100 ")

        assertEquals("100", progressDao.getExerciseProgressById(week3ProgressId)!!.weightText)
        val week2 = repository.loadTrainingState(targetProgramId, training, 2, 4)
        assertEquals("100", week2.exerciseStates.single().progress.weightText)
    }
}
