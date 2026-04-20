package com.emanuel.gymhelper.ui

data class ExerciseSessionItem(
    val exerciseProgressId: Long,
    val name: String,
    val setsReps: String,
    val setProgressText: String,
    val intensityType: String,
    val isDone: Boolean,
    val isSkipped: Boolean,
    val isOngoing: Boolean,
    val currentWeight: String?,
    val timerRemainingSeconds: Int?,
    val expanded: Boolean,
    val showCompletedDivider: Boolean
)
