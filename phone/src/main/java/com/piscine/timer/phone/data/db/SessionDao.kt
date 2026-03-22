package com.piscine.timer.phone.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTimestamp DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions")
    fun getSessionCount(): Flow<Int>

    @Query("SELECT SUM(totalDistanceMeters) FROM sessions")
    fun getTotalDistance(): Flow<Int?>

    @Query("SELECT MIN(bestLapTimeMs) FROM sessions WHERE poolLengthMeters = 25")
    fun getBestLap25m(): Flow<Long?>

    @Query("SELECT MIN(bestLapTimeMs) FROM sessions WHERE poolLengthMeters = 50")
    fun getBestLap50m(): Flow<Long?>

    @Query("SELECT SUM(totalTimeMs) FROM sessions")
    fun getTotalTime(): Flow<Long?>

    @Query("SELECT MIN(bestLapTimeMs) FROM sessions WHERE poolLengthMeters = :meters")
    fun getBestLapForPool(meters: Int): Flow<Long?>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)
}
