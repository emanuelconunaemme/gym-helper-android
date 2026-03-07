package com.emanuel.gymhelper.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.emanuel.gymhelper.data.local.room.entity.ExerciseEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseTypeEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseWeekEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseWeekSetEntity
import com.emanuel.gymhelper.data.local.room.entity.ProgramEntity
import com.emanuel.gymhelper.data.local.room.entity.TrainingEntity
import com.emanuel.gymhelper.data.local.room.relation.ProgramWithTrainings

@Dao
interface ProgramDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: ProgramEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainings(trainings: List<TrainingEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseTypes(exerciseTypes: List<ExerciseTypeEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseWeeks(exerciseWeeks: List<ExerciseWeekEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseWeekSets(exerciseWeekSets: List<ExerciseWeekSetEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM programs")
    suspend fun getProgramCount(): Int

    @Query("DELETE FROM programs")
    suspend fun deleteAllPrograms()

    @Transaction
    @Query("SELECT * FROM programs WHERE programId = :programId")
    suspend fun getProgramById(programId: Long): ProgramWithTrainings?

    @Transaction
    @Query("SELECT * FROM programs ORDER BY programId DESC LIMIT 1")
    suspend fun getLatestProgram(): ProgramWithTrainings?

    @Transaction
    @Query("SELECT * FROM programs ORDER BY programId DESC")
    suspend fun getAllPrograms(): List<ProgramWithTrainings>
}
