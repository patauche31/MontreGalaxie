package com.piscine.timer.presentation

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.piscine.timer.data.PreferencesManager
import com.piscine.timer.data.db.SwimDatabase
import com.piscine.timer.data.repository.SessionRepository
import com.piscine.timer.data.sync.WearSyncManager
import com.piscine.timer.domain.LapDetector
import com.piscine.timer.domain.SensorLogger
import com.piscine.timer.domain.SessionManager
import com.piscine.timer.domain.StrokeCounter
import com.piscine.timer.domain.model.PoolLength
import com.piscine.timer.domain.model.SessionState
import com.piscine.timer.presentation.screens.CustomLengthScreen
import com.piscine.timer.presentation.screens.ReadyScreen
import com.piscine.timer.presentation.screens.SettingsScreen
import com.piscine.timer.presentation.screens.SummaryScreen
import com.piscine.timer.presentation.screens.SwimmingScreen
import com.piscine.timer.presentation.theme.PiscineTimerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Routes de navigation ──────────────────────────────────────────────────────
object Routes {
    const val READY         = "ready"
    const val SWIMMING      = "swimming"
    const val SUMMARY       = "summary"
    const val SETTINGS      = "settings"
    const val CUSTOM_LENGTH = "custom_length"
}

class MainActivity : ComponentActivity() {

    private lateinit var sensorLogger  : SensorLogger
    private lateinit var sessionManager: SessionManager
    private lateinit var navController : NavHostController
    private lateinit var sessionRepository: SessionRepository
    private lateinit var prefs: PreferencesManager

    private var lapDetector   : LapDetector?   = null
    private var strokeCounter : StrokeCounter? = null
    private var spmCollectJob : Job?           = null

    /** SPM courant partagé vers Compose */
    private val _currentSpm = MutableStateFlow(0f)

    /** Enregistre un lap en incluant le nombre de coups depuis le dernier lap */
    private fun recordLapWithStrokes() {
        val strokes = strokeCounter?.getAndResetCount() ?: 0
        sessionManager.recordLap(strokes)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val db = SwimDatabase.getInstance(applicationContext)
        sessionRepository = SessionRepository(db.sessionDao())
        prefs = PreferencesManager(applicationContext)
        sensorLogger   = SensorLogger(applicationContext)
        sessionManager = SessionManager(
            sensorLogger = if (prefs.debugLogging.value) sensorLogger else null
        )

        setContent {
            PiscineTimerTheme {
                navController = rememberSwipeDismissableNavController()

                val session      by sessionManager.session.collectAsState()
                val elapsedMs    by sessionManager.elapsedMs.collectAsState()
                val currentLap   by sessionManager.currentLapMs.collectAsState()
                val autoDetectOn by prefs.autoDetectEnabled.collectAsState()
                val vibrationOn  by prefs.vibrationEnabled.collectAsState()
                val currentSpm   by _currentSpm.asStateFlow().collectAsState()

                var lastCustomMeters by remember { mutableStateOf(prefs.lastCustomMeters) }

                // ── Cycle de vie LapDetector ──────────────────────────────────
                LaunchedEffect(session.state, autoDetectOn) {
                    when (session.state) {
                        SessionState.RUNNING -> {
                            if (autoDetectOn) {
                                if (lapDetector == null) {
                                    lapDetector = LapDetector(
                                        context       = this@MainActivity,
                                        poolLength    = session.poolLength,
                                        logger        = if (prefs.debugLogging.value) sensorLogger else null,
                                        onLapDetected = {
                                            Log.d("LapDetector", "Virage détecté auto !")
                                            recordLapWithStrokes()
                                        }
                                    )
                                    lapDetector?.start()
                                } else {
                                    lapDetector?.resume()
                                }
                            } else {
                                lapDetector?.stop()
                                lapDetector = null
                            }
                        }
                        SessionState.PAUSED -> lapDetector?.pause()
                        SessionState.FINISHED, SessionState.IDLE -> {
                            lapDetector?.stop()
                            lapDetector = null
                        }
                    }
                }

                // ── Cycle de vie StrokeCounter ────────────────────────────────
                LaunchedEffect(session.state) {
                    when (session.state) {
                        SessionState.RUNNING -> {
                            if (strokeCounter == null) {
                                strokeCounter = StrokeCounter(this@MainActivity)
                                spmCollectJob?.cancel()
                                spmCollectJob = lifecycleScope.launch {
                                    strokeCounter!!.currentSpm.collect { spm ->
                                        _currentSpm.value = spm
                                    }
                                }
                                strokeCounter?.start()
                            } else {
                                strokeCounter?.resume()
                            }
                        }
                        SessionState.PAUSED -> {
                            strokeCounter?.pause()
                            _currentSpm.value = 0f
                        }
                        SessionState.FINISHED, SessionState.IDLE -> {
                            strokeCounter?.stop()
                            strokeCounter = null
                            spmCollectJob?.cancel()
                            _currentSpm.value = 0f
                        }
                    }
                }

                SwipeDismissableNavHost(
                    navController    = navController,
                    startDestination = Routes.READY
                ) {

                    // ── Accueil ───────────────────────────────────────────────
                    composable(Routes.READY) {
                        ReadyScreen(
                            onStart25m    = {
                                sessionManager.start(PoolLength.POOL_25)
                                navController.navigate(Routes.SWIMMING)
                            },
                            onStart50m    = {
                                sessionManager.start(PoolLength.POOL_50)
                                navController.navigate(Routes.SWIMMING)
                            },
                            onStartCustom = {
                                navController.navigate(Routes.CUSTOM_LENGTH)
                            },
                            onSettings    = {
                                navController.navigate(Routes.SETTINGS)
                            }
                        )
                    }

                    // ── Longueur personnalisée ────────────────────────────────
                    composable(Routes.CUSTOM_LENGTH) {
                        CustomLengthScreen(
                            initialMeters = lastCustomMeters,
                            onStart = { meters ->
                                lastCustomMeters = meters
                                prefs.setLastCustomMeters(meters)
                                sessionManager.start(PoolLength.POOL_CUSTOM, meters)
                                navController.navigate(Routes.SWIMMING)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // ── Nage ──────────────────────────────────────────────────
                    composable(Routes.SWIMMING) {
                        SwimmingScreen(
                            session          = session,
                            elapsedMs        = elapsedMs,
                            currentLapMs     = currentLap,
                            autoDetectActive = autoDetectOn &&
                                               lapDetector?.isAvailable == true &&
                                               session.state == SessionState.RUNNING,
                            vibrationEnabled = vibrationOn,
                            currentSpm       = currentSpm,
                            onLap            = { recordLapWithStrokes() },
                            onTogglePause    = { sessionManager.togglePause() },
                            onFinish         = {
                                sessionManager.finish()
                                val snap    = sessionManager.session.value
                                val elapsed = sessionManager.elapsedMs.value
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val savedId = sessionRepository.saveSession(snap, elapsed)
                                    Log.d("Room", "Session sauvegardée — id=$savedId laps=${snap.lapCount}")
                                    sessionRepository.getSessionById(savedId)?.let { entity ->
                                        WearSyncManager.sendSession(applicationContext, entity)
                                    }
                                }
                                navController.navigate(Routes.SUMMARY)
                            }
                        )
                    }

                    // ── Récapitulatif ─────────────────────────────────────────
                    composable(Routes.SUMMARY) {
                        SummaryScreen(
                            session        = session,
                            totalElapsedMs = elapsedMs,
                            onNewSession   = {
                                sessionManager.reset()
                                navController.navigate(Routes.READY) {
                                    popUpTo(Routes.READY) { inclusive = true }
                                }
                            }
                        )
                    }

                    // ── Paramètres ────────────────────────────────────────────
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            prefs  = prefs,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentRoute = navController.currentDestination?.route
        if (currentRoute != Routes.SWIMMING) return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_1, 294 -> {
                if (event?.repeatCount == 0) recordLapWithStrokes()
                true
            }
            KeyEvent.KEYCODE_STEM_2, 295 -> {
                sessionManager.finish()
                navController.navigate(Routes.SUMMARY)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lapDetector?.stop()
        strokeCounter?.stop()
        spmCollectJob?.cancel()
        sessionManager.clear()
    }
}
