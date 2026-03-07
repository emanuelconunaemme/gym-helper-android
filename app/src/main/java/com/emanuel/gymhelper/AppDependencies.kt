package com.emanuel.gymhelper

import android.content.Context
import androidx.room.Room
import com.emanuel.gymhelper.data.importer.ProgramJsonImporter
import com.emanuel.gymhelper.data.local.room.GymHelperDatabase
import com.emanuel.gymhelper.data.tracker.WorkoutTrackerRepository

object AppDependencies {

    @Volatile
    private var database: GymHelperDatabase? = null

    fun database(context: Context): GymHelperDatabase {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                GymHelperDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(GymHelperDatabase.MIGRATION_1_2)
                .build()
                .also { database = it }
        }
    }

    fun importer(context: Context): ProgramJsonImporter = ProgramJsonImporter(database(context))

    fun trackerRepository(context: Context): WorkoutTrackerRepository {
        return WorkoutTrackerRepository(database(context))
    }

    private const val DATABASE_NAME = "gym_helper.db"
}
