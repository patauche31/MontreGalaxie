package com.piscine.timer.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.piscine.timer.domain.model.PoolLength
import kotlin.math.sqrt

/**
 * Détection automatique des virages — algorithme combiné accéléromètre + gyroscope.
 *
 * Inspiré de l'approche Garmin/Huawei :
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  SWIMMING  →  (acc faible 250ms)  →  WALL_CANDIDATE             │
 * │  WALL_CANDIDATE  →  (rotation ≥ 2.5 rad en 3s)  →  LAP ✅       │
 * │  WALL_CANDIDATE  →  (timeout 3s sans rotation)  →  SWIMMING     │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * Pourquoi ça marche mieux :
 *   - La nage normale génère du mouvement continu → acc rarement < seuil
 *   - Au virage : bref moment quasi-statique (toucher le mur / retournement)
 *     PUIS rotation 180° → les deux signaux ensemble = virage certain
 *
 * @param context        Context Android
 * @param poolLength     Longueur du bassin → durée minimum entre deux laps
 * @param onLapDetected  Callback déclenché à chaque virage validé
 */
class LapDetector(
    context: Context,
    poolLength: PoolLength,
    private val onLapDetected: () -> Unit,
    private val logger: SensorLogger? = null
) : SensorEventListener {

    // ── Constantes de l'algorithme ─────────────────────────────────────────

    companion object {
        private const val TAG = "LapDetector"

        // Accélération linéaire max pour "contact mur" (m/s²)
        // À 0 = parfaitement immobile. En nage on dépasse souvent 2-3 m/s².
        // ⬇ 1.5→1.2 : plus strict — élimine les pauses de nage normale (dos crawlé)
        const val WALL_ACC_THRESHOLD   = 1.2f

        // Durée de quasi-immobilité pour valider le contact mur (ms)
        // ⬆ 250→500 : en recovery dos crawlé le bras est immobile ~200ms max → éliminé
        const val WALL_DURATION_MS     = 500L

        // Fenêtre max après contact mur pour détecter la rotation (ms)
        const val ROTATION_WINDOW_MS   = 4_000L

        // Rotation cumulée minimum pour confirmer le virage (~230°)
        // ⬆ 2.5→4.0 : un vrai virage dos = retournement ~180° + push = ~4 rad cumulés
        const val MIN_ROTATION_RAD     = 4.0f

        // Vitesse angulaire minimale pour accumuler (filtre bruit) (rad/s)
        const val MIN_ANG_VEL          = 0.8f

        // Durée maximale du contact mur (si > → nageur arrêté, pas virage)
        const val MAX_WALL_DURATION_MS = 4_000L
    }

    // ── État interne ───────────────────────────────────────────────────────

    private enum class State { SWIMMING, WALL_CANDIDATE }

    private var state = State.SWIMMING
    private var isActive = false

    private var wallStartMs        = 0L   // début de la phase quasi-statique
    private var wallConfirmedMs    = 0L   // fin de la phase quasi-statique (wall validé)
    private var accumulatedRotation = 0f  // rotation cumulée depuis wallConfirmedMs
    private var lastDetectionMs    = 0L   // timestamp du dernier virage validé
    private var lastGyroNanos      = 0L   // pour calcul Δt gyroscope

    // Durée minimum entre deux laps selon la longueur du bassin
    private val minLapMs: Long = when (poolLength) {
        PoolLength.POOL_25   -> 40_000L   // 40s min — nageur loisir ~40-70s/25m (⬆ 30→40)
        PoolLength.POOL_50   -> 70_000L   // 70s min — nageur loisir ~70-120s/50m (⬆ 60→70)
        PoolLength.POOL_CUSTOM -> 25_000L // 25s min pour bassin personnalisé
    }

    // ── Capteurs ──────────────────────────────────────────────────────────

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // TYPE_LINEAR_ACCELERATION = accéléromètre sans gravité → 0 quand immobile
    private val linearAccSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val gyroSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator =
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    val isAvailable: Boolean get() = linearAccSensor != null && gyroSensor != null

    // ── SensorEventListener ───────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (!isActive) return
        val nowMs = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> handleAccelerometer(event, nowMs)
            Sensor.TYPE_GYROSCOPE           -> handleGyroscope(event, nowMs)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ── Traitement accéléromètre ──────────────────────────────────────────

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
            event     = if (state == State.WALL_CANDIDATE) "WALL_CAND" else ""
        )

        // Cooldown global : ignorer pendant minLapMs après un lap
        if (nowMs - lastDetectionMs < minLapMs) return

        when (state) {

            State.SWIMMING -> {
                if (acc < WALL_ACC_THRESHOLD) {
                    // Début possible du contact mur
                    wallStartMs = nowMs
                    state = State.WALL_CANDIDATE
                    wallConfirmedMs = 0L
                    accumulatedRotation = 0f
                    lastGyroNanos = 0L
                    logger?.event("WALL_START acc=${"%.2f".format(acc)}")
                    Log.v(TAG, "⬜ Wall candidate — acc=${"%.2f".format(acc)} m/s²")
                }
            }

            State.WALL_CANDIDATE -> {
                val wallDuration = nowMs - wallStartMs

                if (wallDuration > MAX_WALL_DURATION_MS) {
                    // Trop long immobile → nageur arrêté, pas un virage
                    resetToSwimming("Timeout immobilité ${wallDuration}ms")
                    return
                }

                if (acc >= WALL_ACC_THRESHOLD) {
                    // Mouvement repris
                    if (wallDuration < WALL_DURATION_MS) {
                        // Quasi-immobilité trop courte → faux positif
                        resetToSwimming("Faux contact trop court ${wallDuration}ms")
                    } else if (wallConfirmedMs == 0L) {
                        // ✅ Contact mur validé ! On commence à accumuler la rotation
                        wallConfirmedMs = nowMs
                        logger?.event("WALL_CONFIRMED dur=${wallDuration}ms")
                        Log.d(TAG, "✅ Contact mur validé (${wallDuration}ms) — attente rotation…")
                    } else {
                        // On est en phase rotation, vérifie le timeout
                        if (nowMs - wallConfirmedMs > ROTATION_WINDOW_MS) {
                            resetToSwimming("Fenêtre rotation expirée")
                        }
                    }
                }
            }
        }
    }

    // ── Traitement gyroscope ──────────────────────────────────────────────

    private fun handleGyroscope(event: SensorEvent, nowMs: Long) {
        val dt = if (lastGyroNanos == 0L) 0f
                 else (event.timestamp - lastGyroNanos) / 1_000_000_000f
        lastGyroNanos = event.timestamp

        if (dt <= 0f || dt > 0.5f) return

        val magnitude = sqrt(
            event.values[0] * event.values[0] +
            event.values[1] * event.values[1] +
            event.values[2] * event.values[2]
        )

        if (magnitude < MIN_ANG_VEL) return
        if (state != State.WALL_CANDIDATE || wallConfirmedMs == 0L) return
        if (nowMs - lastDetectionMs < minLapMs) return

        // Fenêtre rotation expirée ?
        if (nowMs - wallConfirmedMs > ROTATION_WINDOW_MS) {
            resetToSwimming("Fenêtre rotation expirée (gyro)")
            return
        }

        // Accumulation rotation
        accumulatedRotation += magnitude * dt
        Log.v(TAG, "↻ rotation=${" %.2f".format(accumulatedRotation)} rad (target=$MIN_ROTATION_RAD)")

        if (accumulatedRotation >= MIN_ROTATION_RAD) {
            logger?.event("LAP_AUTO rot=${"%.2f".format(accumulatedRotation)}rad")
            Log.d(TAG, "🏊 VIRAGE! contact+rotation=${"%.2f".format(accumulatedRotation)} rad")
            triggerLap(nowMs)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun triggerLap(nowMs: Long) {
        lastDetectionMs = nowMs
        state = State.SWIMMING
        wallStartMs = 0L
        wallConfirmedMs = 0L
        accumulatedRotation = 0f
        lastGyroNanos = 0L
        vibrate()
        onLapDetected()
    }

    private fun resetToSwimming(reason: String) {
        Log.v(TAG, "↩ SWIMMING — $reason")
        state = State.SWIMMING
        wallStartMs = 0L
        wallConfirmedMs = 0L
        accumulatedRotation = 0f
        lastGyroNanos = 0L
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

    // ── Cycle de vie ──────────────────────────────────────────────────────

    fun start() {
        if (!isAvailable) {
            Log.w(TAG, "Capteurs non disponibles")
            return
        }
        isActive = true
        resetToSwimming("start()")
        lastDetectionMs = System.currentTimeMillis()
        sensorManager.registerListener(this, linearAccSensor, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, gyroSensor,       SensorManager.SENSOR_DELAY_GAME)
        Log.d(TAG, "Démarré — minLap=${minLapMs/1000}s | acc<${WALL_ACC_THRESHOLD} → rot≥${MIN_ROTATION_RAD}rad")
    }

    fun pause() {
        isActive = false
        resetToSwimming("pause()")
    }

    fun resume() {
        if (!isAvailable) return
        isActive = true
        resetToSwimming("resume()")
        lastDetectionMs = System.currentTimeMillis()
        Log.d(TAG, "Reprise")
    }

    fun stop() {
        isActive = false
        sensorManager.unregisterListener(this)
        resetToSwimming("stop()")
        Log.d(TAG, "Arrêté")
    }
}
