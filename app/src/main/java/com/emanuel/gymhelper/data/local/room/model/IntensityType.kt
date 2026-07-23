package com.emanuel.gymhelper.data.local.room.model

import com.emanuel.gymhelper.R

enum class IntensityType(val dbValue: String) {
    NONE("none"),
    REST_PAUSE_2X("rest_pause_2x"),
    STRIPPING_2X("stripping_2x"),
    NEGATIVA_3S("negativa_3s");

    companion object {
        fun fromDbValue(value: String): IntensityType {
            return entries.firstOrNull { it.dbValue == value } ?: NONE
        }
    }

    fun shouldShowIntensityBadge(): Boolean = this != NONE

    fun badgeTextRes(): Int = when (this) {
        NONE -> error("No badge for NONE")
        REST_PAUSE_2X -> R.string.intensity_rest_pause_2x_short
        STRIPPING_2X -> R.string.intensity_stripping_2x_short
        NEGATIVA_3S -> R.string.intensity_negativa_3s_short
    }

    fun accentColorRes(): Int = when (this) {
        NONE -> R.color.card_stroke_default
        REST_PAUSE_2X -> R.color.intensity_rest_pause
        STRIPPING_2X -> R.color.intensity_stripping
        NEGATIVA_3S -> R.color.intensity_stripping
    }
}
