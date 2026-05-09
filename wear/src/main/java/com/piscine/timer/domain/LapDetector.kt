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
 * Détection automatique des virages — algorithme double capteur.
 *
 * Données réelles CSV (Galaxy Watch 5 Pro, dos crawlé 25m) :
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  Au virage (demi-tour) :                                                │
 * │    1. Rotation gyroscope élevée  (demi-tour corps : 2–8 rad/s)         │
 * │    2. Suivi d'un pic accéléro    (push-off mur   : 8–20 m/s²)          │
 * │                                                                         │
 * │  Pendant la nage normale :                                              │
 * │    Rotation : 0.5–1.5 rad/s  (roulis de nage)                          │
 * │    Accéléro : 0.5–10 m/s²   (coups de bras)                            │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Algorithme :
 *   1. Gyroscope détecte rotation > ROTATION_THRESHOLD  → "pré-virage" mémorisé
 *   2. Dans les ROTATION_WINDOW_MS suivants, si accéléro > PUSH_THRESHOLD
 *      ET cooldown minLapMs respecté → virage validé ✅
 *
 * Avantage : les coups de bras forts ne déclenchent pas de faux positifs
 * car ils ne sont pas précédés d'une rotation de demi-tour.
 */
class LapDetector(
    context: Context,
    poolLength: PoolLength,
    private val onLapDetected: () -> Unit,
    private val logger: SensorLogger? = null
) : SensorEventListener {

    companion object {
        private const val TAG = "LapDetector"

        /**
         * Seuil rotation gyroscope (rad/s).
         * Demi-tour dos crawlé : 2–8 rad/s.
         * Roulis nage normale  : 0.5–1.5 rad/s.
         */
        const val ROTATION_THRESHOLD = 3.5f

        /**
         * Seuil push-off accéléromètre (m/s²).
         * Push-off mur : 8–20 m/s².
         * Coup de bras : 0.5–10 m/s².
         */
        const val PUSH_THRESHOLD = 8.0f

        /**
         * Fenêtre après la rotation pour attendre le push-off (ms).
         * Le demi-tour se fait ~0.5–2s avant le push-off.
         */
        const val ROTATION_WINDOW_MS = 3_000L

        /** Après détection, bloquer les taps manuels X ms (anti double-compte) */
        const val POST_DETECT_LOCKOUT_MS = 4_000L
    }

    // ── Durée minimum entre deux virages ─────────────────────────────────────
    private val minLapMs: Long = when (poolLength) {
        PoolLength.POOL_25     -> 65_000L   // 65s — données réelles 26/04/2026
        PoolLength.POOL_50     -> 70_000L
        PoolLength.POOL_CUSTOM -> 30_000L
    }

    // ── État interne ──────────────────────────────────────────────────────────
    private var isActive          = false
    private var lastDetectionMs   = 0L    // timestamp du dernier virage validé
    private var lastRotationMs    = 0L    // timestamp de la dernière rotation élevée
    private var inAccPeak         = false // évite de compter plusieurs fois le même pic

    /** True pendant POST_DETECT_LOCKOUT_MS après une détection — bloque les taps manuels */
    val isInLockout: Boolean
        get() = System.currentTimeMillis() - lastDetectionMs < POST_DETECT_LOCKOUT_MS

    // ── Handler – expire la fenêtre de rotation ───────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private val rotationExpireRunnable = Runnable {
        if (lastRotationMs > 0L) {
            Log.v(TAG, "⏳ Fenêtre rotation expirée sans push-off")
            lastRotationMs = 0L
        }
    }

    // ── Capteurs ──────────────────────────────────────────────────────────────
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val linearAccSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val gyroSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator =
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    val isAvailable: Boolean get() = linearAccSensor != null

    // ── SensorEventListener ───────────────────────────────────────────────────
    override fun onSensorChanged(event: SensorEvent) {
        if (!isActive) return
        val nowMs = System.currentTimeMillis()

        when (event.sensor.type) {

            Sensor.TYPE_GYROSCOPE -> {
                val rot = sqrt(
                    event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]
                )
                // Log TOUTES les rotations > 1 rad/s pour calibration
                if (rot > 1.0f) {
                    logger?.event("GYR rot=${"%.2f".format(rot)}rad/s")
                }
                if (rot > ROTATION_THRESHOLD) {
                    val cooldownOk = nowMs - lastDetectionMs >= minLapMs
                    if (cooldownOk && lastRotationMs == 0L) {
                        // Nouvelle rotation détectée → ouvre la fenêtre push-off
                        lastRotationMs = nowMs
                        inAccPeak      = false
                        Log.v(TAG, "🔄 Rotation détectée : ${"%.1f".format(rot)} rad/s → attente push-off")
                        // Si pas de push-off dans ROTATION_WINDOW_MS → réinitialiser
                        handler.removeCallbacks(rotationExpireRunnable)
                        handler.postDelayed(rotationExpireRunnable, ROTATION_WINDOW_MS)
                    }
                }
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
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

                if (acc > PUSH_THRESHOLD) {
                    if (!inAccPeak) {
                        inAccPeak = true
                        val rotationRecent = lastRotationMs > 0L &&
                                             nowMs - lastRotationMs <= ROTATION_WINDOW_MS
                        val cooldownOk     = nowMs - lastDetectionMs >= minLapMs

                        if (rotationRecent && cooldownOk) {
                            // Rotation + push-off = virage confirmé ✅
                            handler.removeCallbacks(rotationExpireRunnable)
                            logger?.event("LAP_AUTO rot+push acc=${"%.1f".format(acc)}m/s2 cooldown=${nowMs - lastDetectionMs}ms")
                            Log.d(TAG, "🏊 VIRAGE confirmé! rotation + push ${"%.1f".format(acc)} m/s²")
                            lastRotationMs = 0L
                            triggerLap(nowMs)
                        } else if (!rotationRecent && cooldownOk) {
                            Log.v(TAG, "💪 Pic ${"%.1f".format(acc)} m/s² sans rotation préalable → ignoré")
                        }
                    }
                } else {
                    inAccPeak = false
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ── Déclenchement virage ──────────────────────────────────────────────────
    private fun triggerLap(nowMs: Long) {
        lastDetectionMs = nowMs
        vibrate()
        onLapDetected()
    }

    /** Vibration forte — 1 longue impulsion bien perceptible sous l'eau */
    private fun vibrate() {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 400, 100, 200),
                intArrayOf(0, 255, 0, 255),
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
        inAccPeak       = false
        lastRotationMs  = 0L
        lastDetectionMs = System.currentTimeMillis()

        sensorManager.registerListener(this, linearAccSensor, SensorManager.SENSOR_DELAY_GAME)
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        Log.d(TAG, "Démarré — minLap=${minLapMs/1000}s | rot>${ROTATION_THRESHOLD}rad/s | push>${PUSH_THRESHOLD}m/s²")
    }

    fun pause() {
        isActive       = false
        lastRotationMs = 0L
        inAccPeak      = false
        handler.removeCallbacks(rotationExpireRunnable)
        Log.d(TAG, "Pause")
    }

    fun resume() {
        if (!isAvailable) return
        isActive        = true
        inAccPeak       = false
        lastRotationMs  = 0L
        lastDetectionMs = System.currentTimeMillis()
        Log.d(TAG, "Reprise")
    }

    fun stop() {
        isActive       = false
        inAccPeak      = false
        lastRotationMs = 0L
        handler.removeCallbacks(rotationExpireRunnable)
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Arrêté")
    }
}
