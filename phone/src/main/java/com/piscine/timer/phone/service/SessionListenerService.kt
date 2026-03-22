package com.piscine.timer.phone.service

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.piscine.timer.phone.data.db.SessionEntity
import com.piscine.timer.phone.data.db.SwimDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reçoit automatiquement les sessions depuis la montre
 * dès qu'elle est à portée Bluetooth.
 */
class SessionListenerService : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path?.startsWith("/piscine/session/") == true) {

                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val session = SessionEntity(
                    id                  = dataMap.getLong("id"),
                    startTimestamp      = dataMap.getLong("startTimestamp"),
                    poolLengthMeters    = dataMap.getInt("poolLengthMeters"),
                    lapCount            = dataMap.getInt("lapCount"),
                    totalDistanceMeters = dataMap.getInt("totalDistanceMeters"),
                    totalTimeMs         = dataMap.getLong("totalTimeMs"),
                    averageLapTimeMs    = dataMap.getLong("averageLapTimeMs"),
                    bestLapTimeMs       = dataMap.getLong("bestLapTimeMs"),
                    bestLapNumber       = dataMap.getInt("bestLapNumber"),
                    worstLapTimeMs      = dataMap.getLong("worstLapTimeMs"),
                    worstLapNumber      = dataMap.getInt("worstLapNumber"),
                    lapTimesJson        = dataMap.getString("lapTimesJson") ?: "",
                    strokeCountsJson    = dataMap.getString("strokeCountsJson") ?: ""
                )

                scope.launch {
                    SwimDatabase.getInstance(applicationContext)
                        .sessionDao()
                        .insertSession(session)
                    Log.d("SessionListener", "✅ Session reçue — id=${session.id} laps=${session.lapCount}")
                }
            }
        }
    }
}
