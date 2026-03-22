package com.piscine.timer.data.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.piscine.timer.data.db.SessionEntity
import kotlinx.coroutines.tasks.await

/**
 * Envoie les sessions terminées vers l'app téléphone
 * via Wearable Data Layer (Bluetooth, sans internet).
 *
 * Protocol :
 *   Path  : /piscine/session/<timestamp>
 *   Clés  : toutes les colonnes de SessionEntity
 */
object WearSyncManager {

    private const val TAG = "WearSync"
    private const val PATH_PREFIX = "/piscine/session/"

    suspend fun sendSession(context: Context, session: SessionEntity) {
        try {
            val dataClient: DataClient = Wearable.getDataClient(context)

            val request = PutDataMapRequest.create(PATH_PREFIX + session.startTimestamp).apply {
                dataMap.apply {
                    putLong("id",                  session.id)
                    putLong("startTimestamp",      session.startTimestamp)
                    putInt ("poolLengthMeters",    session.poolLengthMeters)
                    putInt ("lapCount",            session.lapCount)
                    putInt ("totalDistanceMeters", session.totalDistanceMeters)
                    putLong("totalTimeMs",         session.totalTimeMs)
                    putLong("averageLapTimeMs",    session.averageLapTimeMs)
                    putLong("bestLapTimeMs",       session.bestLapTimeMs)
                    putInt ("bestLapNumber",       session.bestLapNumber)
                    putLong("worstLapTimeMs",      session.worstLapTimeMs)
                    putInt ("worstLapNumber",      session.worstLapNumber)
                    putString("lapTimesJson",      session.lapTimesJson)
                    putString("strokeCountsJson",  session.strokeCountsJson)
                }
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
            Log.d(TAG, "✅ Session envoyée au téléphone — id=${session.id} laps=${session.lapCount}")

        } catch (e: Exception) {
            // Pas de téléphone connecté → session reste dans Room, sera synchro plus tard
            Log.w(TAG, "⚠️ Téléphone non disponible — session conservée en local: ${e.message}")
        }
    }
}
