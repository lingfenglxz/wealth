package com.demo.wealth.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun intListToString(value: List<Int>): String = value.joinToString(",")

    @TypeConverter
    fun stringToIntList(value: String): List<Int> =
        value.split(",").mapNotNull { it.trim().toIntOrNull() }

    @TypeConverter
    fun doubleListToString(value: List<Double>): String = value.joinToString(",")

    @TypeConverter
    fun stringToDoubleList(value: String): List<Double> =
        value.split(",").mapNotNull { it.trim().toDoubleOrNull() }
}
