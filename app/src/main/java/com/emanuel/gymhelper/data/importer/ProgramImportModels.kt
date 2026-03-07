package com.emanuel.gymhelper.data.importer

data class ProgramImportPayload(
    val program: ProgramImport
)

data class ProgramImport(
    val name: String,
    val prehabMarkdown: String,
    val numberOfWeeks: Int,
    val unloadWeek: Boolean,
    val trainings: List<TrainingImport>
)

data class TrainingImport(
    val name: String,
    val exercises: List<ExerciseImport>
)

data class ExerciseImport(
    val exerciseType: ExerciseTypeImport,
    val intensityType: String,
    val restSeconds: Int,
    val weeks: List<ExerciseWeekImport>
)

data class ExerciseTypeImport(
    val name: String,
    val youtubeVideo: String?,
    val notes: String?
)

data class ExerciseWeekImport(
    val week: Int,
    val setRepetitions: List<SetRepetitionImport>
)

data class SetRepetitionImport(
    val setCount: Int,
    val repetitionsMin: Int,
    val repetitionsMax: Int
)
