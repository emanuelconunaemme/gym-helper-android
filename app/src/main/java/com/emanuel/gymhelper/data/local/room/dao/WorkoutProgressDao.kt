package com.emanuel.gymhelper.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emanuel.gymhelper.data.local.room.entity.ExerciseProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.HiitProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.ProgramProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.SetProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.TrainingProgressEntity
import com.emanuel.gymhelper.data.local.room.progress.ProgressStatus

@Dao
interface WorkoutProgressDao {

    @Query("SELECT * FROM program_progress WHERE programId = :programId LIMIT 1")
    suspend fun getProgramProgress(programId: Long): ProgramProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgramProgress(programProgress: ProgramProgressEntity): Long

    @Query(
        """
        UPDATE program_progress
        SET currentWeek = :currentWeek, updatedAtEpochMs = :updatedAtEpochMs
        WHERE programProgressId = :programProgressId
        """
    )
    suspend fun updateProgramWeek(
        programProgressId: Long,
        currentWeek: Int,
        updatedAtEpochMs: Long
    )

    @Query("SELECT * FROM training_progress WHERE trainingId = :trainingId AND weekNumber = :weekNumber LIMIT 1")
    suspend fun getTrainingProgress(trainingId: Long, weekNumber: Int): TrainingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainingProgress(trainingProgress: TrainingProgressEntity): Long

    @Query(
        """
        UPDATE training_progress
        SET status = :status, updatedAtEpochMs = :updatedAtEpochMs
        WHERE trainingProgressId = :trainingProgressId
        """
    )
    suspend fun updateTrainingStatus(
        trainingProgressId: Long,
        status: String,
        updatedAtEpochMs: Long
    )

    @Query("SELECT * FROM exercise_progress WHERE trainingProgressId = :trainingProgressId")
    suspend fun getExerciseProgressForTraining(trainingProgressId: Long): List<ExerciseProgressEntity>

    @Query("SELECT * FROM exercise_progress WHERE exerciseProgressId = :exerciseProgressId LIMIT 1")
    suspend fun getExerciseProgressById(exerciseProgressId: Long): ExerciseProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseProgress(exerciseProgress: ExerciseProgressEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetProgress(setProgress: List<SetProgressEntity>): List<Long>

    @Query("SELECT * FROM set_progress WHERE exerciseProgressId = :exerciseProgressId ORDER BY setIndex ASC")
    suspend fun getSetProgressForExercise(exerciseProgressId: Long): List<SetProgressEntity>

    @Query(
        """
        SELECT * FROM set_progress
        WHERE exerciseProgressId = :exerciseProgressId
        AND status = :pendingStatus
        ORDER BY setIndex ASC
        LIMIT 1
        """
    )
    suspend fun getNextPendingSet(
        exerciseProgressId: Long,
        pendingStatus: String = ProgressStatus.PENDING
    ): SetProgressEntity?

    @Query(
        """
        UPDATE set_progress
        SET status = :status, updatedAtEpochMs = :updatedAtEpochMs
        WHERE setProgressId = :setProgressId
        """
    )
    suspend fun updateSetStatus(
        setProgressId: Long,
        status: String,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE set_progress
        SET status = :status, updatedAtEpochMs = :updatedAtEpochMs
        WHERE exerciseProgressId = :exerciseProgressId
        AND status = :pendingStatus
        """
    )
    suspend fun updateAllPendingSetsStatus(
        exerciseProgressId: Long,
        status: String,
        updatedAtEpochMs: Long,
        pendingStatus: String = ProgressStatus.PENDING
    )

    @Query(
        """
        UPDATE exercise_progress
        SET completedSets = :completedSets,
            skippedSets = :skippedSets,
            status = :status,
            weightText = :weightText,
            completedAtEpochMs = :completedAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE exerciseProgressId = :exerciseProgressId
        """
    )
    suspend fun updateExerciseProgress(
        exerciseProgressId: Long,
        completedSets: Int,
        skippedSets: Int,
        status: String,
        weightText: String?,
        completedAtEpochMs: Long?,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE exercise_progress
        SET weightText = :weightText, updatedAtEpochMs = :updatedAtEpochMs
        WHERE exerciseProgressId = :exerciseProgressId
        """
    )
    suspend fun updateExerciseWeight(
        exerciseProgressId: Long,
        weightText: String?,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE exercise_progress
        SET plannedSets = :plannedSets, updatedAtEpochMs = :updatedAtEpochMs
        WHERE exerciseProgressId = :exerciseProgressId
        """
    )
    suspend fun updateExercisePlannedSets(
        exerciseProgressId: Long,
        plannedSets: Int,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        SELECT weightText
        FROM exercise_progress
        WHERE exerciseId = :exerciseId
          AND exerciseProgressId != :excludeExerciseProgressId
          AND weightText IS NOT NULL
          AND TRIM(weightText) != ''
        ORDER BY COALESCE(completedAtEpochMs, 0) DESC, exerciseProgressId DESC
        LIMIT 1
        """
    )
    suspend fun getLastWeightForExercise(
        exerciseId: Long,
        excludeExerciseProgressId: Long
    ): String?

    @Query(
        """
        SELECT weightText
        FROM exercise_progress
        WHERE exerciseId = :exerciseId
          AND weekNumber = :weekNumber
          AND weightText IS NOT NULL
          AND TRIM(weightText) != ''
        ORDER BY COALESCE(completedAtEpochMs, 0) DESC, exerciseProgressId DESC
        LIMIT 1
        """
    )
    suspend fun getWeightForExerciseAtWeek(
        exerciseId: Long,
        weekNumber: Int
    ): String?

    @Query(
        """
        SELECT COUNT(*)
        FROM exercise_progress ep
        INNER JOIN training_progress tp ON tp.trainingProgressId = ep.trainingProgressId
        WHERE tp.programId = :programId
          AND tp.weekNumber = :weekNumber
          AND ep.status = :status
        """
    )
    suspend fun countExercisesByStatus(
        programId: Long,
        weekNumber: Int,
        status: String
    ): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM training_progress
        WHERE programId = :programId
          AND weekNumber = :weekNumber
          AND status = :status
        """
    )
    suspend fun countTrainingsByStatus(
        programId: Long,
        weekNumber: Int,
        status: String
    ): Int

    @Query(
        """
        SELECT * FROM hiit_progress
        WHERE programId = :programId
          AND weekNumber = :weekNumber
        LIMIT 1
        """
    )
    suspend fun getHiitProgress(programId: Long, weekNumber: Int): HiitProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiitProgress(progress: HiitProgressEntity): Long

    @Query(
        """
        UPDATE hiit_progress
        SET completedCycles = :completedCycles,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE hiitProgressId = :hiitProgressId
        """
    )
    suspend fun updateHiitProgress(
        hiitProgressId: Long,
        completedCycles: Int,
        updatedAtEpochMs: Long
    )
}
