package com.emanuel.gymhelper.data.local.room.converter

import androidx.room.TypeConverter
import com.emanuel.gymhelper.data.local.room.model.IntensityType

class RoomConverters {

    @TypeConverter
    fun fromIntensityType(value: IntensityType): String = value.dbValue

    @TypeConverter
    fun toIntensityType(value: String): IntensityType = IntensityType.fromDbValue(value)
}
