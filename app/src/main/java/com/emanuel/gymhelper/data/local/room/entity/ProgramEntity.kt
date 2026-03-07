package com.emanuel.gymhelper.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true)
    val programId: Long = 0,
    val name: String,
    val prehabMarkdown: String,
    val numberOfWeeks: Int,
    val unloadWeek: Boolean
)
