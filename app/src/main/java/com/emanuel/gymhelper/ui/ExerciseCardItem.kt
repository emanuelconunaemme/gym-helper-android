package com.emanuel.gymhelper.ui

data class ExerciseCardItem(
    val exerciseProgressId: Long,
    val exerciseId: Long,
    val name: String,
    val restSeconds: Int,
    val intensityLabel: String,
    val intensityTypeValue: String,
    val plannedSets: Int,
    val completedSets: Int,
    val skippedSets: Int,
    val nextSetNumber: Int?,
    val statusLabel: String,
    val isCompleted: Boolean,
    val isSkipped: Boolean,
    val currentWeightText: String?,
    val lastSessionWeightText: String?,
    val detailsText: String,
    val expanded: Boolean
)
