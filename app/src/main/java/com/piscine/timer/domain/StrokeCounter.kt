package com.piscine.timer.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Comptage des coups de bras en temps réel.
 *
 * Algorithme : détection de pics par hystérésis sur l'accéléromètre linéaire lissé.
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │  BELOW  →  (signal > HIGH_THRESHOLD + gap > 400ms)  │
 *   │         →  coup compté, state = ABOVE               │
 *   │  ABOVE  →  (signal < LOW_THRESHOLD)  →  BELOW       │
 *   └──────────────────────────────────────────────────────┘
 *
 * Métriques exposées :
 *   - currentSpm  : cadence instantanée (moy. glissante 4 coups)
 *   - getAndResetCount() : coups depuis le dernier lap, puis remise à zéro
 */
class StrokeCounter(context: Context) : SensorEventListener {

    // ── Constantes ────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "StrokeCounter"

        /** Seuil haut : un coup de bras énergique dépasse 3 m/s² */
        private const val HIGH_THRESHOLD = 3.0f

        /** Seuil bas : récupération — dessous avant de pouvoir compter le suivant */
        private const val LOW_THRESHOLD  = 1.0f

        /** Intervalle minimum entre deux coups (ms) → 150 SPM max */
        private const val MIN_STROKE_GAP_MS = 400L

        /** Lissage exponentiel — 0.3 = assez réactif mais sans pics de bruit */
        private const val EMA_ALPHA = 0.3f

        /** Nombre de coups conservés pour le calcul SPM glissant */
        private const val SPM_WINDOW = 4
    }

    // ── Capteur ───────────────────────────────────────────────────────────────

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    val isAvailable: Boolean get() = accSensor != null

    // ── État interne ──────────────────────────────────────────────────────────

    private var isActive         = false
    private var smoothed         = 0f
    private var isAboveThreshold = false
    private var strokeCount      = 0
    private var lastStrokeMs     = 0L

    /** Timestamps des derniers SPM_WINDOW coups (pour calcul glissant) */
    private val strokeTimestamps = ArrayDeque<Long>(SPM_WINDOW + 1)

    // ── StateFlow public ──────────────────────────────────────────────────────

    private val _currentSpm = MutableStateFlow(0f)
    val currentSpm: StateFlow<Float> = _currentSpm.asStateFlow()

    // ── SensorEventListener ───────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (!isActive) return

        val magnitude = sqrt(
            event.values[0] * event.values[0] +
            event.values[1] * event.values[1] +
            event.values[2] * event.values[2]
        )

        // Lissage exponentiel
        smoothed = EMA_ALPHA * magnitude + (1f - EMA_ALPHA) * smoothed

        val nowMs = System.currentTimeMillis()

        when {
            // Front montant : nouveau coup de bras
            !isAboveThreshold
                && smoothed > HIGH_THRESHOLD
                && (nowMs - lastStrokeMs) > MIN_STROKE_GAP_MS -> {

                isAboveThreshold = true
                strokeCount++
                lastStrokeMs = nowMs

                // Mise à jour SPM glissant
                strokeTimestamps.addLast(nowMs)
                if (strokeTimestamps.size > SPM_WINDOW + 1) strokeTimestamps.removeFirst()
                if (strokeTimestamps.size >= 2) {
                    val avgIntervalMs = strokeTimestamps
                        .zipWithNext { a, b -> (b - a).toDouble() }
                        .average()
                    _currentSpm.value = (60_000.0 / avgIntervalMs).toFloat()
                }
                Log.v(TAG, "💪 Coup #$strokeCount — ${_currentSpm.value.toInt()} c/min")
            }

            // Front descendant : prêt pour le prochain coup
            isAboveThreshold && smoothed < LOW_THRESHOLD -> {
                isAboveThreshold = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ── API publique ──────────────────────────────────────────────────────────

    /**
     * Retourne le nombre de coups depuis le dernier appel et remet à zéro.
     * Appelé par SessionManager.recordLap().
     */
    fun getAndResetCount(): Int {
        val count = strokeCount
        strokeCount = 0
        strokeTimestamps.clear()
        _currentSpm.value = 0f
        return count
    }

    fun start() {
        isActive         = true
        strokeCount      = 0
        lastStrokeMs     = System.currentTimeMillis()
        smoothed         = 0f
        isAboveThreshold = false
        strokeTimestamps.clear()
        _currentSpm.value = 0f
        accSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        Log.d(TAG, "Démarré")
    }

    fun stop() {
        isActive = false
        sensorManager.unregisterListener(this)
        _currentSpm.value = 0f
        Log.d(TAG, "Arrêté — coups enregistrés=$strokeCount")
    }

    fun pause() {
        isActive = false
    }

    fun resume() {
        isActive     = true
        lastStrokeMs = System.currentTimeMillis()
    }
}
