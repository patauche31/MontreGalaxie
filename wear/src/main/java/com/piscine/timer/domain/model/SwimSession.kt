package com.piscine.timer.domain.model

import com.piscine.timer.domain.model.LapData.Companion.formatTime

enum class SessionState {
    IDLE,       // Pas encore démarré
    RUNNING,    // Chrono en cours
    PAUSED,     // En pause (repos entre séries)
    FINISHED    // Session terminée
}

enum class PoolLength(val meters: Int, val label: String) {
    POOL_25(25, "25m"),
    POOL_50(50, "50m"),
    POOL_CUSTOM(0, "Autre")   // longueur définie par customPoolMeters
}

/**
 * Représente une session de nage complète.
 */
data class SwimSession(
    val poolLength: PoolLength = PoolLength.POOL_25,
    val customPoolMeters: Int  = 33,         // utilisé uniquement si poolLength == POOL_CUSTOM
    val laps: List<LapData>    = emptyList(),
    val state: SessionState    = SessionState.IDLE,
    val startTimeMs: Long      = 0L,
    val totalPausedMs: Long    = 0L
) {
    /** Longueur effective du bassin en mètres */
    val effectivePoolMeters: Int
        get() = if (poolLength == PoolLength.POOL_CUSTOM) customPoolMeters
                else poolLength.meters

    val lapCount: Int get() = laps.size
    val totalDistanceMeters: Int get() = lapCount * effectivePoolMeters

    /** Meilleure longueur */
    val bestLap: LapData? get() = laps.minByOrNull { it.lapTimeMs }

    /** Pire longueur */
    val worstLap: LapData? get() = laps.maxByOrNull { it.lapTimeMs }

    /** Temps moyen par longueur */
    val averageLapTimeMs: Long
        get() = if (laps.isEmpty()) 0L else laps.sumOf { it.lapTimeMs } / laps.size

    val averageLapTimeFormatted: String get() = formatTime(averageLapTimeMs)
}
