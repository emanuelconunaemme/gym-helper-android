package com.emanuel.gymhelper.data.tracker

import androidx.room.withTransaction
import com.emanuel.gymhelper.data.local.room.GymHelperDatabase
import com.emanuel.gymhelper.data.local.room.entity.ExerciseProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.HiitProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.ProgramProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.SetProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.TrainingProgressEntity
import com.emanuel.gymhelper.data.local.room.model.IntensityType
import com.emanuel.gymhelper.data.local.room.progress.ProgressStatus
import com.emanuel.gymhelper.data.local.room.relation.ExerciseWeekWithSets
import com.emanuel.gymhelper.data.local.room.relation.ExerciseWithDetails
import com.emanuel.gymhelper.data.local.room.relation.ProgramWithTrainings
import com.emanuel.gymhelper.data.local.room.relation.TrainingWithExercises

class WorkoutTrackerRepository(
    private val database: GymHelperDatabase
) {

    private val programDao = database.programDao()
    private val progressDao = database.workoutProgressDao()

    suspend fun loadProgramState(): ProgramTrackerState? {
        val program = programDao.getLatestProgram()?.sortedDeep() ?: return null
        val progress = ensureProgramProgress(
            programId = program.program.programId,
            numberOfWeeks = totalWeeksWithUnload(program.program.numberOfWeeks)
        )
        val summary = getWeekSummary(program.program.programId, progress.currentWeek)
        return ProgramTrackerState(program, progress, summary)
    }

    suspend fun updateProgramWeek(programId: Long, numberOfWeeks: Int, requestedWeek: Int): Int {
        val effectiveWeeks = totalWeeksWithUnload(numberOfWeeks)
        val progress = ensureProgramProgress(programId, effectiveWeeks)
        val clampedWeek = requestedWeek.coerceIn(1, effectiveWeeks)
        progressDao.updateProgramWeek(progress.programProgressId, clampedWeek, now())
        return clampedWeek
    }

    suspend fun loadTrainingState(
        programId: Long,
        training: TrainingWithExercises,
        weekNumber: Int,
        programNumberOfWeeks: Int
    ): TrainingTrackerState {
        return database.withTransaction {
            val unloadWeekNumber = totalWeeksWithUnload(programNumberOfWeeks)
            val isUnloadWeek = weekNumber == unloadWeekNumber
            val trainingProgress = ensureTrainingProgress(programId, training.training.trainingId, weekNumber)
            ensureExerciseProgress(
                trainingProgressId = trainingProgress.trainingProgressId,
                training = training,
                weekNumber = weekNumber,
                unloadWeekNumber = unloadWeekNumber
            )

            val exerciseProgressByExerciseId = progressDao
                .getExerciseProgressForTraining(trainingProgress.trainingProgressId)
                .associateBy { it.exerciseId }

            val exerciseStates = training.exercises.mapNotNull { exercise ->
                val progress = exerciseProgressByExerciseId[exercise.exercise.exerciseId] ?: return@mapNotNull null
                val sets = progressDao.getSetProgressForExercise(progress.exerciseProgressId)
                val weekPlan = resolveWeekPlanForWeek(
                    exercise = exercise,
                    weekNumber = weekNumber,
                    unloadWeekNumber = unloadWeekNumber
                )
                val lastWeight = if (isUnloadWeek) {
                    progressDao.getWeightForExerciseAtWeek(
                        exerciseId = exercise.exercise.exerciseId,
                        weekNumber = FIRST_WEEK_NUMBER
                    ) ?: progressDao.getLastWeightForExercise(
                        exerciseId = exercise.exercise.exerciseId,
                        excludeExerciseProgressId = progress.exerciseProgressId
                    )
                } else {
                    progressDao.getLastWeightForExercise(
                        exerciseId = exercise.exercise.exerciseId,
                        excludeExerciseProgressId = progress.exerciseProgressId
                    )
                }
                ExerciseTrackerState(
                    exercise = exercise,
                    weekPlan = weekPlan,
                    progress = progress,
                    setProgress = sets,
                    lastSessionWeightText = lastWeight,
                    effectiveIntensityType = if (isUnloadWeek) {
                        IntensityType.NONE
                    } else {
                        exercise.exercise.intensityType
                    },
                    effectiveRestSeconds = exercise.exercise.restSeconds
                )
            }

            TrainingTrackerState(trainingProgress, exerciseStates)
        }
    }

    suspend fun saveWeight(exerciseProgressId: Long, weightText: String) {
        val normalized = weightText.trim().takeIf { it.isNotBlank() }
        progressDao.updateExerciseWeight(exerciseProgressId, normalized, now())
    }

    suspend fun finishNextSetWithRest(exerciseProgressId: Long, weightText: String): TrackerActionResult {
        saveWeight(exerciseProgressId, weightText)
        val nextSet = progressDao.getNextPendingSet(exerciseProgressId) ?: return TrackerActionResult.noop()
        progressDao.updateSetStatus(nextSet.setProgressId, ProgressStatus.DONE, now())
        return recalculateExerciseAndTraining(exerciseProgressId)
    }

    suspend fun markNextSetDone(exerciseProgressId: Long, weightText: String): TrackerActionResult {
        saveWeight(exerciseProgressId, weightText)
        val nextSet = progressDao.getNextPendingSet(exerciseProgressId) ?: return TrackerActionResult.noop()
        progressDao.updateSetStatus(nextSet.setProgressId, ProgressStatus.DONE, now())
        return recalculateExerciseAndTraining(exerciseProgressId)
    }

    suspend fun skipNextSet(exerciseProgressId: Long, weightText: String): TrackerActionResult {
        saveWeight(exerciseProgressId, weightText)
        val nextSet = progressDao.getNextPendingSet(exerciseProgressId) ?: return TrackerActionResult.noop()
        progressDao.updateSetStatus(nextSet.setProgressId, ProgressStatus.SKIPPED, now())
        return recalculateExerciseAndTraining(exerciseProgressId)
    }

    suspend fun markExerciseDone(exerciseProgressId: Long, weightText: String): TrackerActionResult {
        saveWeight(exerciseProgressId, weightText)
        progressDao.updateAllPendingSetsStatus(exerciseProgressId, ProgressStatus.DONE, now())
        return recalculateExerciseAndTraining(exerciseProgressId)
    }

    suspend fun skipExercise(exerciseProgressId: Long, weightText: String): TrackerActionResult {
        saveWeight(exerciseProgressId, weightText)
        progressDao.updateAllPendingSetsStatus(exerciseProgressId, ProgressStatus.SKIPPED, now())
        return recalculateExerciseAndTraining(exerciseProgressId)
    }

    suspend fun resetExercise(exerciseProgressId: Long): TrackerActionResult {
        val now = now()
        progressDao.updateAllSetStatuses(exerciseProgressId, ProgressStatus.PENDING, now)
        return recalculateExerciseAndTraining(exerciseProgressId)
    }

    suspend fun skipTraining(trainingProgressId: Long) {
        updateTrainingProgressBulk(trainingProgressId, ProgressStatus.SKIPPED)
    }

    suspend fun resetTraining(trainingProgressId: Long) {
        updateTrainingProgressBulk(trainingProgressId, ProgressStatus.PENDING)
    }

    suspend fun markTrainingDone(trainingProgressId: Long) {
        updateTrainingProgressBulk(trainingProgressId, ProgressStatus.DONE)
    }

    suspend fun getWeekSummary(programId: Long, weekNumber: Int): WeekSummary {
        val doneExercises = progressDao.countExercisesByStatus(programId, weekNumber, ProgressStatus.DONE)
        val skippedExercises = progressDao.countExercisesByStatus(programId, weekNumber, ProgressStatus.SKIPPED)
        val doneTrainings = progressDao.countTrainingsByStatus(programId, weekNumber, ProgressStatus.DONE)
        val skippedTrainings = progressDao.countTrainingsByStatus(programId, weekNumber, ProgressStatus.SKIPPED)
        return WeekSummary(doneExercises, skippedExercises, doneTrainings, skippedTrainings)
    }

    suspend fun getHiitProgress(programId: Long, weekNumber: Int): HiitProgressEntity {
        val existing = progressDao.getHiitProgress(programId, weekNumber)
        if (existing != null) return existing

        val now = now()
        val id = progressDao.insertHiitProgress(
            HiitProgressEntity(
                programId = programId,
                weekNumber = weekNumber,
                updatedAtEpochMs = now
            )
        )
        return HiitProgressEntity(
            hiitProgressId = id,
            programId = programId,
            weekNumber = weekNumber,
            updatedAtEpochMs = now
        )
    }

    suspend fun updateHiitCompletedCycles(programId: Long, weekNumber: Int, completedCycles: Int) {
        val progress = getHiitProgress(programId, weekNumber)
        progressDao.updateHiitProgress(
            hiitProgressId = progress.hiitProgressId,
            completedCycles = completedCycles.coerceIn(0, HIIT_TOTAL_CYCLES),
            updatedAtEpochMs = now()
        )
    }

    private suspend fun ensureProgramProgress(programId: Long, numberOfWeeks: Int): ProgramProgressEntity {
        val existing = progressDao.getProgramProgress(programId)
        if (existing == null) {
            val now = now()
            val id = progressDao.insertProgramProgress(
                ProgramProgressEntity(
                    programId = programId,
                    currentWeek = 1,
                    updatedAtEpochMs = now
                )
            )
            return ProgramProgressEntity(id, programId, 1, now)
        }

        val clampedWeek = existing.currentWeek.coerceIn(1, numberOfWeeks.coerceAtLeast(1))
        if (clampedWeek != existing.currentWeek) {
            progressDao.updateProgramWeek(existing.programProgressId, clampedWeek, now())
            return existing.copy(currentWeek = clampedWeek, updatedAtEpochMs = now())
        }
        return existing
    }

    private suspend fun ensureTrainingProgress(
        programId: Long,
        trainingId: Long,
        weekNumber: Int
    ): TrainingProgressEntity {
        val existing = progressDao.getTrainingProgress(trainingId, weekNumber)
        if (existing != null) return existing

        val now = now()
        val id = progressDao.insertTrainingProgress(
            TrainingProgressEntity(
                programId = programId,
                trainingId = trainingId,
                weekNumber = weekNumber,
                status = ProgressStatus.PENDING,
                updatedAtEpochMs = now
            )
        )
        return TrainingProgressEntity(
            trainingProgressId = id,
            programId = programId,
            trainingId = trainingId,
            weekNumber = weekNumber,
            status = ProgressStatus.PENDING,
            updatedAtEpochMs = now
        )
    }

    private suspend fun ensureExerciseProgress(
        trainingProgressId: Long,
        training: TrainingWithExercises,
        weekNumber: Int,
        unloadWeekNumber: Int
    ) {
        val existingByExerciseId = progressDao
            .getExerciseProgressForTraining(trainingProgressId)
            .associateBy { it.exerciseId }

        training.exercises.forEach { exercise ->
            val exerciseId = exercise.exercise.exerciseId
            val weekPlan = resolveWeekPlanForWeek(
                exercise = exercise,
                weekNumber = weekNumber,
                unloadWeekNumber = unloadWeekNumber
            )
            val basePlannedSets = weekPlan?.setRepetitions?.sumOf { it.setCount } ?: 0
            val isUnloadWeek = weekNumber == unloadWeekNumber
            val restPauseBonusSets = if (
                !isUnloadWeek &&
                basePlannedSets > 0 &&
                exercise.exercise.intensityType == IntensityType.REST_PAUSE_2X
            ) {
                2
            } else {
                0
            }
            val expectedPlannedSets = basePlannedSets + restPauseBonusSets

            val existing = existingByExerciseId[exerciseId]
            if (existing == null) {
                val now = now()
                val exerciseProgressId = progressDao.insertExerciseProgress(
                    ExerciseProgressEntity(
                        trainingProgressId = trainingProgressId,
                        exerciseId = exerciseId,
                        weekNumber = weekNumber,
                        plannedSets = expectedPlannedSets,
                        updatedAtEpochMs = now
                    )
                )
                if (expectedPlannedSets > 0) {
                    progressDao.insertSetProgress(
                        (1..expectedPlannedSets).map { setIndex ->
                            SetProgressEntity(
                                exerciseProgressId = exerciseProgressId,
                                setIndex = setIndex,
                                status = ProgressStatus.PENDING,
                                updatedAtEpochMs = now
                            )
                        }
                    )
                }
            } else {
                val sets = progressDao.getSetProgressForExercise(existing.exerciseProgressId)
                var needsRecalc = false
                if (existing.plannedSets != expectedPlannedSets) {
                    progressDao.updateExercisePlannedSets(
                        exerciseProgressId = existing.exerciseProgressId,
                        plannedSets = expectedPlannedSets,
                        updatedAtEpochMs = now()
                    )
                    needsRecalc = true
                }

                val maxSetIndex = sets.maxOfOrNull { it.setIndex } ?: 0
                if (maxSetIndex < expectedPlannedSets) {
                    val now = now()
                    progressDao.insertSetProgress(
                        ((maxSetIndex + 1)..expectedPlannedSets).map { setIndex ->
                            SetProgressEntity(
                                exerciseProgressId = existing.exerciseProgressId,
                                setIndex = setIndex,
                                status = ProgressStatus.PENDING,
                                updatedAtEpochMs = now
                            )
                        }
                    )
                    needsRecalc = true
                }

                if (needsRecalc) {
                    recalculateExerciseAndTraining(existing.exerciseProgressId)
                }
            }
        }
    }

    private suspend fun recalculateExerciseAndTraining(exerciseProgressId: Long): TrackerActionResult {
        val current = progressDao.getExerciseProgressById(exerciseProgressId) ?: return TrackerActionResult.noop()
        val setProgress = progressDao.getSetProgressForExercise(exerciseProgressId)

        val completedSets = setProgress.count { it.status == ProgressStatus.DONE }
        val skippedSets = setProgress.count { it.status == ProgressStatus.SKIPPED }
        val resolvedSets = completedSets + skippedSets

        val exerciseStatus = when {
            current.plannedSets == 0 -> ProgressStatus.DONE
            resolvedSets == 0 -> ProgressStatus.PENDING
            resolvedSets >= current.plannedSets -> {
                if (completedSets == 0) ProgressStatus.SKIPPED else ProgressStatus.DONE
            }
            else -> ProgressStatus.PENDING
        }

        val now = now()
        progressDao.updateExerciseProgress(
            exerciseProgressId = exerciseProgressId,
            completedSets = completedSets,
            skippedSets = skippedSets,
            status = exerciseStatus,
            weightText = current.weightText,
            completedAtEpochMs = if (exerciseStatus == ProgressStatus.PENDING) null else now,
            updatedAtEpochMs = now
        )

        val allExerciseProgress = progressDao.getExerciseProgressForTraining(current.trainingProgressId).map {
            if (it.exerciseProgressId == exerciseProgressId) {
                it.copy(
                    completedSets = completedSets,
                    skippedSets = skippedSets,
                    status = exerciseStatus
                )
            } else {
                it
            }
        }

        val trainingStatus = when {
            allExerciseProgress.isEmpty() -> ProgressStatus.PENDING
            allExerciseProgress.all { it.status != ProgressStatus.PENDING } -> {
                if (allExerciseProgress.all { it.status == ProgressStatus.SKIPPED }) {
                    ProgressStatus.SKIPPED
                } else {
                    ProgressStatus.DONE
                }
            }
            else -> ProgressStatus.PENDING
        }
        progressDao.updateTrainingStatus(current.trainingProgressId, trainingStatus, now)

        return TrackerActionResult(
            exerciseStatus = exerciseStatus,
            trainingStatus = trainingStatus,
            exerciseFinished = exerciseStatus != ProgressStatus.PENDING,
            trainingFinished = trainingStatus != ProgressStatus.PENDING
        )
    }

    private suspend fun updateTrainingProgressBulk(
        trainingProgressId: Long,
        finalStatus: String
    ) {
        val currentExercises = progressDao.getExerciseProgressForTraining(trainingProgressId)
        val now = now()

        currentExercises.forEach { exercise ->
            when (finalStatus) {
                ProgressStatus.PENDING -> {
                    progressDao.updateAllSetStatuses(exercise.exerciseProgressId, ProgressStatus.PENDING, now)
                    progressDao.updateExerciseProgress(
                        exerciseProgressId = exercise.exerciseProgressId,
                        completedSets = 0,
                        skippedSets = 0,
                        status = ProgressStatus.PENDING,
                        weightText = exercise.weightText,
                        completedAtEpochMs = null,
                        updatedAtEpochMs = now
                    )
                }
                ProgressStatus.SKIPPED -> {
                    progressDao.updateAllSetStatuses(exercise.exerciseProgressId, ProgressStatus.SKIPPED, now)
                    progressDao.updateExerciseProgress(
                        exerciseProgressId = exercise.exerciseProgressId,
                        completedSets = 0,
                        skippedSets = exercise.plannedSets,
                        status = ProgressStatus.SKIPPED,
                        weightText = exercise.weightText,
                        completedAtEpochMs = now,
                        updatedAtEpochMs = now
                    )
                }
                ProgressStatus.DONE -> {
                    progressDao.updateAllSetStatuses(exercise.exerciseProgressId, ProgressStatus.DONE, now)
                    progressDao.updateExerciseProgress(
                        exerciseProgressId = exercise.exerciseProgressId,
                        completedSets = exercise.plannedSets,
                        skippedSets = 0,
                        status = ProgressStatus.DONE,
                        weightText = exercise.weightText,
                        completedAtEpochMs = now,
                        updatedAtEpochMs = now
                    )
                }
            }
        }

        progressDao.updateTrainingStatus(trainingProgressId, finalStatus, now)
    }

    private fun ProgramWithTrainings.sortedDeep(): ProgramWithTrainings {
        return copy(
            trainings = trainings
                .sortedBy { it.training.sortOrder }
                .map { training ->
                    training.copy(
                        exercises = training.exercises
                            .sortedBy { it.exercise.sortOrder }
                            .map { exercise ->
                                exercise.copy(
                                    weeks = exercise.weeks
                                        .sortedBy { it.week.weekNumber }
                                        .map { week ->
                                            week.copy(
                                                setRepetitions = week.setRepetitions.sortedBy { it.sortOrder }
                                            )
                                        }
                                )
                            }
                    )
                }
        )
    }

    private fun resolveWeekPlanForWeek(
        exercise: ExerciseWithDetails,
        weekNumber: Int,
        unloadWeekNumber: Int
    ): ExerciseWeekWithSets? {
        return if (weekNumber == unloadWeekNumber) {
            buildUnloadWeekPlan(exercise, weekNumber)
        } else {
            exercise.weeks.firstOrNull { it.week.weekNumber == weekNumber }
        }
    }

    private fun buildUnloadWeekPlan(
        exercise: ExerciseWithDetails,
        unloadWeekNumber: Int
    ): ExerciseWeekWithSets? {
        val source = exercise.weeks.firstOrNull { it.week.weekNumber == FIRST_WEEK_NUMBER } ?: return null
        val sortedSets = source.setRepetitions.sortedBy { it.sortOrder }
        val totalSets = sortedSets.sumOf { it.setCount }
        val targetTotalSets = (totalSets - 1).coerceAtLeast(0)
        var remaining = targetTotalSets
        val adjustedSets = sortedSets.mapNotNull { original ->
            if (remaining <= 0) return@mapNotNull null
            val allocated = original.setCount.coerceAtMost(remaining)
            remaining -= allocated
            original.copy(setCount = allocated)
        }

        return source.copy(
            week = source.week.copy(weekNumber = unloadWeekNumber),
            setRepetitions = adjustedSets
        )
    }

    private fun totalWeeksWithUnload(numberOfWeeks: Int): Int {
        return numberOfWeeks.coerceAtLeast(1) + UNLOAD_EXTRA_WEEK
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        private const val HIIT_TOTAL_CYCLES = 4
        private const val UNLOAD_EXTRA_WEEK = 1
        private const val FIRST_WEEK_NUMBER = 1
    }
}

data class ProgramTrackerState(
    val program: ProgramWithTrainings,
    val programProgress: ProgramProgressEntity,
    val weekSummary: WeekSummary
)

data class TrainingTrackerState(
    val trainingProgress: TrainingProgressEntity,
    val exerciseStates: List<ExerciseTrackerState>
)

data class ExerciseTrackerState(
    val exercise: ExerciseWithDetails,
    val weekPlan: ExerciseWeekWithSets?,
    val progress: ExerciseProgressEntity,
    val setProgress: List<SetProgressEntity>,
    val lastSessionWeightText: String?,
    val effectiveIntensityType: IntensityType,
    val effectiveRestSeconds: Int
)

data class WeekSummary(
    val doneExercises: Int,
    val skippedExercises: Int,
    val doneTrainings: Int,
    val skippedTrainings: Int
)

data class TrackerActionResult(
    val exerciseStatus: String,
    val trainingStatus: String,
    val exerciseFinished: Boolean,
    val trainingFinished: Boolean
) {
    companion object {
        fun noop(): TrackerActionResult {
            return TrackerActionResult(
                exerciseStatus = ProgressStatus.PENDING,
                trainingStatus = ProgressStatus.PENDING,
                exerciseFinished = false,
                trainingFinished = false
            )
        }
    }
}
