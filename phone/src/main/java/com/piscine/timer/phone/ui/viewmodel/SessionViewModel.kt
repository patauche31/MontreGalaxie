package com.piscine.timer.phone.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.piscine.timer.phone.data.db.SessionDao
import kotlinx.coroutines.launch

class SessionViewModel(private val dao: SessionDao) : ViewModel() {
    val sessions      = dao.getAllSessions()
    val sessionCount  = dao.getSessionCount()
    val totalDistance = dao.getTotalDistance()
    val totalTime     = dao.getTotalTime()
    val bestLap25m    = dao.getBestLap25m()
    val bestLap50m    = dao.getBestLap50m()

    fun deleteSession(id: Long) {
        viewModelScope.launch { dao.deleteSession(id) }
    }

    fun insertDemoSessions() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val day = 86_400_000L
            // 6 séances simulées sur 3 semaines, bassin 25m, 4 longueurs chacune
            val demoData = listOf(
                // (joursAvant, lapTimes ms, strokes)
                Triple(20, listOf(42000L, 44000L, 43500L, 45000L), listOf(22, 23, 22, 24)),
                Triple(17, listOf(40000L, 41000L, 42000L, 40500L), listOf(21, 22, 21, 21)),
                Triple(14, listOf(39000L, 40000L, 38500L, 41000L), listOf(20, 21, 20, 22)),
                Triple(10, listOf(38000L, 39000L, 37500L, 38500L), listOf(19, 20, 19, 20)),
                Triple(6,  listOf(37000L, 38000L, 37000L, 36500L), listOf(18, 19, 18, 18)),
                Triple(1,  listOf(35500L, 36000L, 35000L, 36500L), listOf(17, 18, 17, 18))
            )
            demoData.forEach { (daysAgo, laps, strokes) ->
                val total   = laps.sum()
                val avg     = total / laps.size
                val best    = laps.minOrNull() ?: 0L
                val worst   = laps.maxOrNull() ?: 0L
                dao.insertSession(
                    com.piscine.timer.phone.data.db.SessionEntity(
                        id                  = now - daysAgo * day,
                        startTimestamp      = now - daysAgo * day,
                        poolLengthMeters    = 25,
                        lapCount            = laps.size,
                        totalDistanceMeters = laps.size * 25,
                        totalTimeMs         = total.toLong(),
                        averageLapTimeMs    = avg.toLong(),
                        bestLapTimeMs       = best,
                        bestLapNumber       = laps.indexOf(best) + 1,
                        worstLapTimeMs      = worst,
                        worstLapNumber      = laps.indexOf(worst) + 1,
                        lapTimesJson        = laps.joinToString(","),
                        strokeCountsJson    = strokes.joinToString(",")
                    )
                )
            }
        }
    }
}

class SessionViewModelFactory(private val dao: SessionDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SessionViewModel(dao) as T
    }
}
