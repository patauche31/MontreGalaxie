package com.piscine.timer.domain

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enregistre les données brutes de l'accéléromètre dans un fichier CSV.
 *
 * Format CSV :
 *   elapsed_ms, ax, ay, az, magnitude, smoothed, event
 *
 * Récupération via ADB après la séance :
 *   adb -s 192.168.137.148:PORT shell run-as com.piscine.timer \
 *       cat files/sensor_debug_YYYYMMDD_HHmmss.csv > debug.csv
 *
 * Ou lister les fichiers disponibles :
 *   adb -s 192.168.137.148:PORT shell run-as com.piscine.timer ls files/
 */
class SensorLogger(private val context: Context) {

    companion object {
        private const val TAG = "SensorLogger"
    }

    private var writer    : BufferedWriter? = null
    private var startMs   : Long = 0L
    private var file      : File? = null
    private var isActive  = false

    // ── API publique ──────────────────────────────────────────────────────────

    fun start() {
        if (isActive) return
        try {
            val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dir  = context.filesDir
            file     = File(dir, "sensor_debug_$ts.csv")
            writer   = BufferedWriter(FileWriter(file!!))
            startMs  = System.currentTimeMillis()
            isActive = true

            // En-tête CSV
            writer!!.write("elapsed_ms,ax,ay,az,magnitude,smoothed,event\n")
            writer!!.flush()

            Log.d(TAG, "Logger démarré → ${file!!.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur démarrage logger", e)
        }
    }

    /**
     * Appelé à chaque événement capteur (~50Hz).
     * [event] est vide en fonctionnement normal, ou un label au moment d'un événement.
     */
    fun log(ax: Float, ay: Float, az: Float, magnitude: Float, smoothed: Float, event: String = "") {
        if (!isActive) return
        try {
            val elapsed = System.currentTimeMillis() - startMs
            writer!!.write(
                "$elapsed," +
                "${"%.3f".format(ax)}," +
                "${"%.3f".format(ay)}," +
                "${"%.3f".format(az)}," +
                "${"%.3f".format(magnitude)}," +
                "${"%.3f".format(smoothed)}," +
                "$event\n"
            )
            // Flush seulement sur les événements pour éviter ralentissement
            if (event.isNotEmpty()) writer!!.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur écriture log", e)
        }
    }

    /** Marque un événement important dans le flux (LAP_AUTO, LAP_MANUAL, PAUSE, etc.) */
    fun event(label: String) {
        if (!isActive) return
        try {
            val elapsed = System.currentTimeMillis() - startMs
            writer!!.write("$elapsed,0,0,0,0,0,$label\n")
            writer!!.flush()
            Log.d(TAG, "EVENT $label @ ${elapsed}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur event log", e)
        }
    }

    fun stop() {
        if (!isActive) return
        try {
            writer!!.flush()
            writer!!.close()
            isActive = false
            Log.d(TAG, "Logger arrêté — fichier : ${file?.name} (${file?.length()} octets)")
            Log.d(TAG, "Récupérer avec :")
            Log.d(TAG, "  adb shell run-as com.piscine.timer cat files/${file?.name} > debug.csv")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur fermeture logger", e)
        }
    }

    fun isRunning() = isActive

    /** Liste les fichiers debug disponibles dans les fichiers internes */
    fun listFiles(): List<String> =
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith("sensor_debug_") }
            ?.map { it.name }
            ?: emptyList()

    /** Supprime tous les fichiers debug (après analyse) */
    fun clearFiles() {
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith("sensor_debug_") }
            ?.forEach { it.delete() }
        Log.d(TAG, "Fichiers debug supprimés")
    }
}
