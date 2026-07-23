package com.emanuel.gymhelper.ui

data class TrainingMenuItem(
    val trainingProgressId: Long,
    val trainingId: Long,
    val title: String,
    val plannedSets: Int,
    val doneExercises: Int,
    val skippedExercises: Int,
    val totalExercises: Int,
    val status: String
)
