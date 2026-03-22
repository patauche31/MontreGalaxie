package com.piscine.timer.phone.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: Long,
    val startTimestamp: Long,
    val poolLengthMeters: Int,
    val lapCount: Int,
    val totalDistanceMeters: Int,
    val totalTimeMs: Long,
    val averageLapTimeMs: Long,
    val bestLapTimeMs: Long,
    val bestLapNumber: Int,
    val worstLapTimeMs: Long,
    val worstLapNumber: Int,
    val lapTimesJson: String,

    /** Coups de bras par longueur : "18,20,19,21,..." — vide si non mesuré */
    @ColumnInfo(defaultValue = "")
    val strokeCountsJson: String = ""
) {
    fun lapTimesMs(): List<Long> =
        if (lapTimesJson.isBlank()) emptyList()
        else lapTimesJson.split(",").mapNotNull { it.trim().toLongOrNull() }

    fun strokeCountsPerLap(): List<Int> =
        if (strokeCountsJson.isBlank()) emptyList()
        else strokeCountsJson.split(",").mapNotNull { it.trim().toIntOrNull() }
}
