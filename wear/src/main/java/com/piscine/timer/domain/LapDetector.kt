package com.piscine.timer.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.piscine.timer.domain.model.PoolLength
import kotlin.math.sqrt

/**
 * Détection automatique des virages — algorithme basé sur la COULÉE.
 *
 * Observation clé des données CSV réelles (Galaxy Watch 5 Pro, dos crawlé 25m) :
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  Pendant la nage      : événements accéléromètre toutes les ~20ms       │
 * │  Pendant la coulée    : GAP DE 8-10 SECONDES sans aucun événement       │
 * │  → L'OS Wear OS coupe le capteur quand il détecte l'immobilité          │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Pattern détecté au virage (extrait CSV) :
 *   t=43147ms  acc=14.5 m/s²  ← push-off mur (pic énorme)
 *   t=43550ms  acc=5.3        ← dernier event avant coulée
 *   [ ---- GAP 10.7 secondes AUCUN EVENT ---- ]  ← COULÉE détectée ici
 *   t=54259ms  acc=8.3        ← bras reprend, nage continue
 *
 * Algorithme :
 *   1. Chaque event accéléromètre → reschedule un Runnable à +COULE_SILENCE_MS
 *   2. Si le Runnable se déclenche (= silence ≥ 2s) ET qu'un pic récent existait
 *      → virage validé ✅
 *
 * Avantages vs approche rotation :
 *   ✓ Aucun gyroscope nécessaire (moins de batterie)
 *   ✓ Robuste aux virages dos ouverts (pas de culbute)
 *   ✓ Basé sur comportement OS réel mesuré
 */
class LapDetector(
    context: Context,
    poolLength: PoolLength,
    private val onLapDetected: () -> Unit,
    private val logger: SensorLogger? = null
) : SensorEventListener {

    companion object {
        private const val TAG = "LapDetector"

        /** Seuil push-off (m/s²). Push-off réel = 10-15. Nage normale = 5-9. */
        const val PUSH_THRESHOLD     = 5.0f

        /** Durée de silence capteur pour déclarer une coulée (ms).
         *  Données réelles : gaps de 1.5-5s après push-off. 1500ms = bon compromis. */
        const val COULE_SILENCE_MS   = 1500L

        /** Le dernier pic doit être survenu ≤ X ms avant le début du silence. */
        const val PUSH_BEFORE_GAP_MS = 3000L

        /** Après une détection auto, bloquer les taps manuels pendant X ms (anti double-compte) */
        const val POST_DETECT_LOCKOUT_MS = 4000L
    }

    // ── Durée minimum entre deux laps ────────────────────────────────────────
    private val minLapMs: Long = when (poolLength) {
        PoolLength.POOL_25     -> 15_000L  // 15s min (données réelles : ~18s pour 25m rapide)
        PoolLength.POOL_50     -> 35_000L  // 35s min (nageur loisir rapide)
        PoolLength.POOL_CUSTOM -> 12_000L
    }

    // ── État interne ─────────────────────────────────────────────────────────
    private var isActive        = false
    private var lastEventMs     = 0L   // timestamp du dernier événement capteur
    private var lastPeakMs      = 0L   // timestamp du dernier pic > PUSH_THRESHOLD
    private var lastDetectionMs = 0L   // timestamp du dernier virage validé

    /** True pendant POST_DETECT_LOCKOUT_MS après une détection auto — bloque les taps manuels */
    val isInLockout: Boolean
        get() = System.currentTimeMillis() - lastDetectionMs < POST_DETECT_LOCKOUT_MS

    // ── Handler – détecte le silence capteur (coulée) ────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private val couleeRunnable = Runnable {
        val nowMs           = System.currentTimeMillis()
        val silenceDuration = nowMs - lastEventMs
        val peakBeforeGap   = lastEventMs - lastPeakMs   // temps entre dernier pic et début silence

        Log.d(TAG, "🕵️ Coulée check: silence=${silenceDuration}ms, picAvantGap=${peakBeforeGap}ms")
        logger?.event("COULE_CHECK silence=${silenceDuration}ms peakAge=${peakBeforeGap}ms")

        if (silenceDuration >= COULE_SILENCE_MS &&
            lastPeakMs > 0 &&
            peakBeforeGap <= PUSH_BEFORE_GAP_MS &&
            nowMs - lastDetectionMs >= minLapMs
        ) {
            logger?.event("LAP_AUTO coulée=${silenceDuration}ms")
            Log.d(TAG, "🏊 VIRAGE COULÉE validé! silence=${silenceDuration}ms après pic +${peakBeforeGap}ms")
            triggerLap(nowMs)
        }
    }

    // ── Capteurs ─────────────────────────────────────────────────────────────
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // TYPE_LINEAR_ACCELERATION = accélération sans gravité → ~0 quand vraiment immobile
    private val linearAccSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator =
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    val isAvailable: Boolean get() = linearAccSensor != null

    // ── SensorEventListener ──────────────────────────────────────────────────
    override fun onSensorChanged(event: SensorEvent) {
        if (!isActive || event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        handleAccelerometer(event, System.currentTimeMillis())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ── Traitement accéléromètre ──────────────────────────────────────────────
    private fun handleAccelerometer(event: SensorEvent, nowMs: Long) {
        val acc = sqrt(
            event.values[0] * event.values[0] +
            event.values[1] * event.values[1] +
            event.values[2] * event.values[2]
        )

        // Log données brutes
        logger?.log(
            ax        = event.values[0],
            ay        = event.values[1],
            az        = event.values[2],
            magnitude = acc,
            smoothed  = acc,
            event     = ""
        )

        // Mise à jour timestamp dernier événement
        lastEventMs = nowMs

        // Détection pic push-off / mouvement vigoureux
        if (acc > PUSH_THRESHOLD) {
            lastPeakMs = nowMs
            Log.v(TAG, "💪 Pic acc=${"%.1f".format(acc)} m/s²")
        }

        // Reschedule détection coulée : si aucun event d'ici COULE_SILENCE_MS → virage
        handler.removeCallbacks(couleeRunnable)
        val cooldownOk = nowMs - lastDetectionMs >= minLapMs
        if (lastPeakMs > 0 && cooldownOk) {
            handler.postDelayed(couleeRunnable, COULE_SILENCE_MS)
        }
    }

    // ── Déclenchement virage ──────────────────────────────────────────────────
    private fun triggerLap(nowMs: Long) {
        lastDetectionMs = nowMs
        lastPeakMs      = 0L
        vibrate()
        onLapDetected()
    }

    /** Vibration 2 impulsions courtes — confirmation virage */
    private fun vibrate() {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 80, 60, 80),
                intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
                -1
            )
        )
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────────
    fun start() {
        if (!isAvailable) {
            Log.w(TAG, "Capteur linéaire non disponible")
            return
        }
        isActive        = true
        lastEventMs     = System.currentTimeMillis()
        lastPeakMs      = 0L
        lastDetectionMs = System.currentTimeMillis()
        sensorManager.registerListener(this, linearAccSensor, SensorManager.SENSOR_DELAY_GAME)
        Log.d(TAG, "Démarré — minLap=${minLapMs/1000}s | push>${PUSH_THRESHOLD} | silence>${COULE_SILENCE_MS}ms")
    }

    fun pause() {
        isActive = false
        handler.removeCallbacks(couleeRunnable)
        Log.d(TAG, "Pause")
    }

    fun resume() {
        if (!isAvailable) return
        isActive        = true
        lastEventMs     = System.currentTimeMillis()
        lastPeakMs      = 0L
        lastDetectionMs = System.currentTimeMillis()
        Log.d(TAG, "Reprise")
    }

    fun stop() {
        isActive = false
        handler.removeCallbacks(couleeRunnable)
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Arrêté")
    }
}
