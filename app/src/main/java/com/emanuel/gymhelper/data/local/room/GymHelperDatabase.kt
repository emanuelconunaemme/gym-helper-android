package com.emanuel.gymhelper.data.local.room

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.emanuel.gymhelper.data.local.room.converter.RoomConverters
import com.emanuel.gymhelper.data.local.room.dao.ProgramDao
import com.emanuel.gymhelper.data.local.room.dao.WorkoutProgressDao
import com.emanuel.gymhelper.data.local.room.entity.ExerciseEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseTypeEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseWeekEntity
import com.emanuel.gymhelper.data.local.room.entity.ExerciseWeekSetEntity
import com.emanuel.gymhelper.data.local.room.entity.HiitProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.ProgramEntity
import com.emanuel.gymhelper.data.local.room.entity.ProgramProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.SetProgressEntity
import com.emanuel.gymhelper.data.local.room.entity.TrainingEntity
import com.emanuel.gymhelper.data.local.room.entity.TrainingProgressEntity

@Database(
    entities = [
        ProgramEntity::class,
        TrainingEntity::class,
        ExerciseTypeEntity::class,
        ExerciseEntity::class,
        ExerciseWeekEntity::class,
        ExerciseWeekSetEntity::class,
        ProgramProgressEntity::class,
        TrainingProgressEntity::class,
        ExerciseProgressEntity::class,
        SetProgressEntity::class,
        HiitProgressEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class GymHelperDatabase : RoomDatabase() {

    abstract fun programDao(): ProgramDao
    abstract fun workoutProgressDao(): WorkoutProgressDao

    companion object {
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `hiit_progress` (
                        `hiitProgressId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `programId` INTEGER NOT NULL,
                        `weekNumber` INTEGER NOT NULL,
                        `completedCycles` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        FOREIGN KEY(`programId`) REFERENCES `programs`(`programId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_hiit_progress_programId`
                    ON `hiit_progress` (`programId`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_hiit_progress_programId_weekNumber`
                    ON `hiit_progress` (`programId`, `weekNumber`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `program_progress` (
                        `programProgressId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `programId` INTEGER NOT NULL,
                        `currentWeek` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        FOREIGN KEY(`programId`) REFERENCES `programs`(`programId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_program_progress_programId`
                    ON `program_progress` (`programId`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `training_progress` (
                        `trainingProgressId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `programId` INTEGER NOT NULL,
                        `trainingId` INTEGER NOT NULL,
                        `weekNumber` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        FOREIGN KEY(`programId`) REFERENCES `programs`(`programId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`trainingId`) REFERENCES `trainings`(`trainingId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_training_progress_programId`
                    ON `training_progress` (`programId`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_training_progress_trainingId`
                    ON `training_progress` (`trainingId`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_training_progress_trainingId_weekNumber`
                    ON `training_progress` (`trainingId`, `weekNumber`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exercise_progress` (
                        `exerciseProgressId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trainingProgressId` INTEGER NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `weekNumber` INTEGER NOT NULL,
                        `plannedSets` INTEGER NOT NULL,
                        `completedSets` INTEGER NOT NULL,
                        `skippedSets` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `weightText` TEXT,
                        `completedAtEpochMs` INTEGER,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        FOREIGN KEY(`trainingProgressId`) REFERENCES `training_progress`(`trainingProgressId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`exerciseId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_exercise_progress_trainingProgressId`
                    ON `exercise_progress` (`trainingProgressId`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_exercise_progress_exerciseId`
                    ON `exercise_progress` (`exerciseId`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_progress_trainingProgressId_exerciseId`
                    ON `exercise_progress` (`trainingProgressId`, `exerciseId`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `set_progress` (
                        `setProgressId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `exerciseProgressId` INTEGER NOT NULL,
                        `setIndex` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        FOREIGN KEY(`exerciseProgressId`) REFERENCES `exercise_progress`(`exerciseProgressId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_set_progress_exerciseProgressId`
                    ON `set_progress` (`exerciseProgressId`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_set_progress_exerciseProgressId_setIndex`
                    ON `set_progress` (`exerciseProgressId`, `setIndex`)
                    """.trimIndent()
                )
            }
        }
    }
}
