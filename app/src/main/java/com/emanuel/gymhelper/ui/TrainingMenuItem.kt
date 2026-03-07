package com.emanuel.gymhelper.ui

data class TrainingMenuItem(
    val trainingId: Long,
    val title: String,
    val doneExercises: Int,
    val skippedExercises: Int,
    val totalExercises: Int,
    val status: String
)
