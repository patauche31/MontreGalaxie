package com.piscine.timer.data.repository

import com.piscine.timer.data.db.SessionDao
import com.piscine.timer.data.db.SessionEntity
import com.piscine.timer.domain.model.SwimSession
import kotlinx.coroutines.flow.Flow

/**
 * Repository — pont entre SessionManager et Room.
 * Convertit SwimSession ↔ SessionEntity.
 */
class SessionRepository(private val dao: SessionDao) {

    /** Toutes les sessions sauvegardées (Flow réactif) */
    val allSessions: Flow<List<SessionEntity>> = dao.getAllSessions()

    /**
     * Sauvegarde une session terminée dans Room.
     * Retourne l'id généré.
     */
    suspend fun saveSession(session: SwimSession, totalTimeMs: Long): Long {
        if (session.lapCount == 0) return -1L

        val best  = session.laps.minByOrNull { it.lapTimeMs }
        val worst = session.laps.maxByOrNull { it.lapTimeMs }
        val avg   = if (session.lapCount > 0)
            session.laps.sumOf { it.lapTimeMs } / session.lapCount else 0L

        val entity = SessionEntity(
            startTimestamp       = System.currentTimeMillis() - totalTimeMs,
            poolLengthMeters     = session.effectivePoolMeters,
            lapCount             = session.lapCount,
            totalDistanceMeters  = session.totalDistanceMeters,
            totalTimeMs          = totalTimeMs,
            averageLapTimeMs     = avg,
            bestLapTimeMs        = best?.lapTimeMs ?: 0L,
            bestLapNumber        = best?.lapNumber ?: 0,
            worstLapTimeMs       = worst?.lapTimeMs ?: 0L,
            worstLapNumber       = worst?.lapNumber ?: 0,
            lapTimesJson         = session.laps.joinToString(",") { it.lapTimeMs.toString() },
            strokeCountsJson     = session.laps.joinToString(",") { it.strokeCount.toString() }
        )

        return dao.insertSession(entity)
    }

    suspend fun getSessionById(id: Long): SessionEntity? = dao.getSessionById(id)
    suspend fun getRecentSessions(limit: Int = 20) = dao.getRecentSessions(limit)
    suspend fun getSessionCount() = dao.getSessionCount()
    suspend fun getTotalDistanceMeters() = dao.getTotalDistanceMeters() ?: 0
    suspend fun getBestLap25m() = dao.getBestLap25m()
    suspend fun getBestLap50m() = dao.getBestLap50m()
    suspend fun deleteSession(id: Long) = dao.deleteSession(id)
}
