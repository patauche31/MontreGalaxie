package com.piscine.timer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room représentant une session de nage complète.
 * Chaque session contient ses laps sérialisés en CSV.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Timestamp UNIX (ms) du début de session */
    val startTimestamp: Long,

    /** Longueur du bassin en mètres (25 ou 50) */
    val poolLengthMeters: Int,

    /** Nombre de longueurs complètes */
    val lapCount: Int,

    /** Distance totale en mètres */
    val totalDistanceMeters: Int,

    /** Durée totale en ms */
    val totalTimeMs: Long,

    /** Temps moyen par longueur en ms */
    val averageLapTimeMs: Long,

    /** Meilleur temps de longueur en ms */
    val bestLapTimeMs: Long,

    /** Numéro de la meilleure longueur */
    val bestLapNumber: Int,

    /** Pire temps de longueur en ms */
    val worstLapTimeMs: Long,

    /** Numéro de la pire longueur */
    val worstLapNumber: Int,

    /**
     * Temps de chaque longueur sérialisés : "1500,1620,1480,..."
     * (en ms, séparés par des virgules)
     */
    val lapTimesJson: String,

    /**
     * Coups de bras par longueur : "18,20,19,21,..."
     * Vide ("") si la mesure StrokeCounter n'était pas active.
     */
    @ColumnInfo(defaultValue = "")
    val strokeCountsJson: String = ""
) {
    /** Parse les coups par longueur → liste vide si non mesuré */
    fun strokeCountsPerLap(): List<Int> =
        if (strokeCountsJson.isBlank()) emptyList()
        else strokeCountsJson.split(",").mapNotNull { it.trim().toIntOrNull() }
}
