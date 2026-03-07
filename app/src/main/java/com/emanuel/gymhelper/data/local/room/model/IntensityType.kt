package com.emanuel.gymhelper.data.local.room.model

enum class IntensityType(val dbValue: String) {
    NONE("none"),
    REST_PAUSE_2X("rest_pause_2x"),
    STRIPPING_2X("stripping_2x");

    companion object {
        fun fromDbValue(value: String): IntensityType {
            return entries.firstOrNull { it.dbValue == value } ?: NONE
        }
    }
}
