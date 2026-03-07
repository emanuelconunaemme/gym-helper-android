package com.emanuel.gymhelper.data.importer

import org.json.JSONArray
import org.json.JSONObject

object ProgramJsonParser {

    fun parse(json: String): ProgramImportPayload {
        val root = JSONObject(json)
        val programJson = root.getJSONObject("program")
        return ProgramImportPayload(
            program = ProgramImport(
                name = programJson.requireString("name"),
                prehabMarkdown = programJson.optString("prehab_markdown", ""),
                numberOfWeeks = programJson.getInt("number_of_weeks"),
                unloadWeek = programJson.optBoolean("unload_week", false),
                trainings = programJson.getJSONArray("trainings").toList().map { trainingJson ->
                    TrainingImport(
                        name = trainingJson.requireString("name"),
                        exercises = trainingJson.getJSONArray("exercises").toList().map { exerciseJson ->
                            ExerciseImport(
                                exerciseType = exerciseJson.getJSONObject("exercise_type").let { exerciseTypeJson ->
                                    ExerciseTypeImport(
                                        name = exerciseTypeJson.requireString("name"),
                                        youtubeVideo = exerciseTypeJson.optNullableString("youtube_video"),
                                        notes = exerciseTypeJson.optNullableString("notes")
                                    )
                                },
                                intensityType = exerciseJson.optString("intensity_type", "none"),
                                restSeconds = exerciseJson.getInt("rest_seconds"),
                                weeks = exerciseJson.getJSONArray("weeks").toList().map { weekJson ->
                                    ExerciseWeekImport(
                                        week = weekJson.getInt("week"),
                                        setRepetitions = weekJson.getJSONArray("set_repetitions").toList().map { setRepJson ->
                                            SetRepetitionImport(
                                                setCount = setRepJson.getInt("set_count"),
                                                repetitionsMin = setRepJson.getInt("repetitions_min"),
                                                repetitionsMax = setRepJson.getInt("repetitions_max")
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        )
    }

    private fun JSONObject.requireString(key: String): String {
        val value = optString(key, "").trim()
        require(value.isNotEmpty()) { "JSON field '$key' is required and cannot be blank." }
        return value
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONArray.toList(): List<JSONObject> {
        return (0 until length()).map { index -> getJSONObject(index) }
    }
}
