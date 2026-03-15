package com.piscine.timer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /** Insère une session (ou la remplace si même id) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    /** Toutes les sessions, plus récentes en premier */
    @Query("SELECT * FROM sessions ORDER BY startTimestamp DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    /** Les N dernières sessions */
    @Query("SELECT * FROM sessions ORDER BY startTimestamp DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int = 20): List<SessionEntity>

    /** Une session par son id */
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    /** Nombre total de sessions */
    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getSessionCount(): Int

    /** Distance totale nagée (toutes sessions) */
    @Query("SELECT SUM(totalDistanceMeters) FROM sessions")
    suspend fun getTotalDistanceMeters(): Int?

    /** Meilleur temps sur bassin 25m */
    @Query("SELECT MIN(bestLapTimeMs) FROM sessions WHERE poolLengthMeters = 25")
    suspend fun getBestLap25m(): Long?

    /** Meilleur temps sur bassin 50m */
    @Query("SELECT MIN(bestLapTimeMs) FROM sessions WHERE poolLengthMeters = 50")
    suspend fun getBestLap50m(): Long?

    /** Supprime une session */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    /** Supprime toutes les sessions (reset) */
    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
