package com.piscine.timer.domain

import com.piscine.timer.domain.model.LapData
import com.piscine.timer.domain.model.PoolLength
import com.piscine.timer.domain.model.SessionState
import com.piscine.timer.domain.model.SwimSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Gère toute la logique métier d'une session de nage.
 * Exposé via StateFlow pour l'UI Compose.
 */
class SessionManager(private val sensorLogger: SensorLogger? = null) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    // ── État observable ──────────────────────────────────────────────────────
    private val _session = MutableStateFlow(SwimSession())
    val session: StateFlow<SwimSession> = _session.asStateFlow()

    // Chrono affiché en temps réel (ms depuis le début, hors pauses)
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    // Temps de la longueur en cours
    private val _currentLapMs = MutableStateFlow(0L)
    val currentLapMs: StateFlow<Long> = _currentLapMs.asStateFlow()

    // Timestamp de début du chrono courant (après démarrage ou reprise)
    private var runStartTimestamp = 0L

    // Temps cumulé avant la dernière pause
    private var elapsedBeforePause = 0L

    // Timestamp du début de la longueur en cours
    private var lapStartElapsed = 0L

    // ── Actions publiques ────────────────────────────────────────────────────

    /** Démarre une nouvelle session */
    fun start(poolLength: PoolLength = PoolLength.POOL_25, customMeters: Int = 33) {
        _session.update {
            SwimSession(
                poolLength       = poolLength,
                customPoolMeters = customMeters,
                state            = SessionState.RUNNING
            )
        }
        elapsedBeforePause = 0L
        lapStartElapsed = 0L
        runStartTimestamp = System.currentTimeMillis()
        sensorLogger?.start()
        startTicker()
    }

    /**
     * Enregistre un temps de passage.
     * Thread-safe : peut être appelé depuis le thread capteur (LapDetector).
     */
    @Synchronized
    fun recordLap(strokeCount: Int = 0) {
        if (_session.value.state != SessionState.RUNNING) return
        val currentElapsed = computeElapsed()
        // maxOf(0) : protection absolue contre les valeurs négatives (race condition)
        val lapTimeMs = maxOf(0L, currentElapsed - lapStartElapsed)

        // Garde : ignorer les laps impossibles (< 5 secondes)
        if (lapTimeMs < 5_000L) return

        val lapNumber = (_session.value.lapCount + 1)

        val newLap = LapData(
            lapNumber   = lapNumber,
            lapTimeMs   = lapTimeMs,
            totalTimeMs = currentElapsed,
            strokeCount = strokeCount
        )

        lapStartElapsed = currentElapsed
        sensorLogger?.event("LAP_MANUAL #$lapNumber t=${lapTimeMs}ms")

        _session.update { it.copy(laps = it.laps + newLap) }
    }

    /** Pause / Reprise (appui long bouton haut) */
    fun togglePause() {
        val current = _session.value
        when (current.state) {
            SessionState.RUNNING -> {
                timerJob?.cancel()
                elapsedBeforePause = computeElapsed()
                sensorLogger?.event("PAUSE")
                _session.update { it.copy(state = SessionState.PAUSED) }
            }
            SessionState.PAUSED -> {
                runStartTimestamp = System.currentTimeMillis()
                sensorLogger?.event("RESUME")
                _session.update { it.copy(state = SessionState.RUNNING) }
                startTicker()
            }
            else -> Unit
        }
    }

    /** Termine la session (bouton bas) */
    fun finish() {
        timerJob?.cancel()
        sensorLogger?.event("FINISH laps=${_session.value.lapCount}")
        sensorLogger?.stop()
        _session.update { it.copy(state = SessionState.FINISHED) }
    }

    /** Remet à zéro pour une nouvelle session */
    fun reset() {
        timerJob?.cancel()
        _session.value = SwimSession()
        _elapsedMs.value = 0L
        _currentLapMs.value = 0L
        elapsedBeforePause = 0L
        lapStartElapsed = 0L
    }

    fun clear() {
        scope.cancel()
    }

    // ── Ticker interne ───────────────────────────────────────────────────────

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                val elapsed = computeElapsed()
                _elapsedMs.value = elapsed
                _currentLapMs.value = elapsed - lapStartElapsed
                delay(100L)   // Mise à jour 10x par seconde (dixièmes de seconde)
            }
        }
    }

    private fun computeElapsed(): Long {
        return elapsedBeforePause + (System.currentTimeMillis() - runStartTimestamp)
    }
}
