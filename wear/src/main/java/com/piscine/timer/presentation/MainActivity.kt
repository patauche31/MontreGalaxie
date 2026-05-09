package com.piscine.timer.presentation

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.piscine.timer.PiscineTimerApp
import com.piscine.timer.service.SwimTimerService
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
import com.piscine.timer.data.db.SwimDatabase
import com.piscine.timer.data.repository.SessionRepository
import com.piscine.timer.data.sync.WearSyncManager
import com.piscine.timer.domain.StrokeCounter
import com.piscine.timer.domain.model.PoolLength
import com.piscine.timer.domain.model.SessionState
import com.piscine.timer.presentation.screens.CustomLengthScreen
import com.piscine.timer.presentation.screens.HistoryScreen
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
    const val HISTORY       = "history"
}

class MainActivity : ComponentActivity() {

    private lateinit var navController : NavHostController
    private lateinit var sessionRepository: SessionRepository

    private var strokeCounter : StrokeCounter? = null
    private var spmCollectJob : Job?           = null

    /** SPM courant partagé vers Compose */
    private val _currentSpm   = MutableStateFlow(0f)
    /** Style de nage détecté */
    private val _swimStyle    = MutableStateFlow(com.piscine.timer.domain.model.SwimStyle.INCONNU)

    /** Enregistre un lap en incluant le nombre de coups depuis le dernier lap */
    private fun recordLapWithStrokes() {
        val app = application as PiscineTimerApp
        val strokes = strokeCounter?.getAndResetCount() ?: 0
        app.sessionManager.recordLap(strokes)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Écran toujours allumé tant que l'app est au premier plan
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = application as PiscineTimerApp
        val sessionManager = app.sessionManager
        val prefs = app.prefs

        val db = SwimDatabase.getInstance(applicationContext)
        sessionRepository = SessionRepository(db.sessionDao())

        // ── Bouton Back pendant la nage = compter une longueur ────────────────
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val state = sessionManager.session.value.state
                if (state == SessionState.RUNNING) {
                    // Appui court bouton bas = passage manuel (backup détection auto)
                    Log.d("MainActivity", "Back = lap manuel")
                    recordLapWithStrokes()
                } else if (state == SessionState.PAUSED) {
                    // En pause : ignorer le back
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        setContent {
            PiscineTimerTheme {
                navController = rememberSwipeDismissableNavController()

                val session      by sessionManager.session.collectAsState()
                val elapsedMs    by sessionManager.elapsedMs.collectAsState()
                val currentLap   by sessionManager.currentLapMs.collectAsState()
                val autoDetectOn by prefs.autoDetectEnabled.collectAsState()
                val vibrationOn  by prefs.vibrationEnabled.collectAsState()
                val currentSpm   by _currentSpm.asStateFlow().collectAsState()
                val swimStyle    by _swimStyle.asStateFlow().collectAsState()

                var lastCustomMeters by remember { mutableStateOf(prefs.lastCustomMeters) }

                // ── Verrouillage écran (bloque bouton HOME pendant la nage) ──
                LaunchedEffect(session.state) {
                    when (session.state) {
                        SessionState.RUNNING -> {
                            try {
                                startLockTask() // pin écran → HOME ne quitte plus l'app
                            } catch (e: Exception) {
                                android.util.Log.w("MainActivity", "startLockTask non disponible: ${e.message}")
                            }
                        }
                        SessionState.PAUSED -> { /* conserver le pin pendant la pause */ }
                        else -> {
                            try {
                                stopLockTask()
                            } catch (e: Exception) { }
                        }
                    }
                }

                // ── Service Ongoing Activity (indicateur sur cadran) ──────────
                LaunchedEffect(session.state) {
                    when (session.state) {
                        SessionState.RUNNING ->
                            startService(Intent(this@MainActivity, SwimTimerService::class.java).apply {
                                action = SwimTimerService.ACTION_START
                                putExtra(SwimTimerService.EXTRA_ELAPSED, elapsedMs)
                                putExtra(SwimTimerService.EXTRA_LAPS, session.lapCount)
                                putExtra(SwimTimerService.EXTRA_POOL_LENGTH, session.poolLength.name)
                            })
                        SessionState.IDLE, SessionState.FINISHED ->
                            startService(Intent(this@MainActivity, SwimTimerService::class.java).apply {
                                action = SwimTimerService.ACTION_STOP
                            })
                        else -> { }
                    }
                }

                // ── Mise à jour du service toutes les secondes ────────────────
                LaunchedEffect(elapsedMs) {
                    if (session.state == SessionState.RUNNING && elapsedMs % 1000L < 100L) {
                        startService(Intent(this@MainActivity, SwimTimerService::class.java).apply {
                            action = SwimTimerService.ACTION_UPDATE
                            putExtra(SwimTimerService.EXTRA_ELAPSED, elapsedMs)
                            putExtra(SwimTimerService.EXTRA_LAPS, session.lapCount)
                        })
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
                                    launch {
                                        strokeCounter!!.currentSpm.collect { spm ->
                                            _currentSpm.value = spm
                                        }
                                    }
                                    launch {
                                        strokeCounter!!.swimStyle.collect { style ->
                                            _swimStyle.value = style
                                        }
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
                    startDestination = Routes.READY,
                    userSwipeEnabled = session.state != SessionState.RUNNING &&
                                       session.state != SessionState.PAUSED
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
                            },
                            onHistory     = {
                                navController.navigate(Routes.HISTORY)
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
                                               session.state == SessionState.RUNNING,
                            vibrationEnabled = vibrationOn,
                            currentSpm       = currentSpm,
                            lapDetector      = null,
                            swimStyle        = swimStyle,
                            onLap            = { recordLapWithStrokes() },
                            onTogglePause    = { sessionManager.togglePause() },
                            onFinish         = {
                                // Capture AVANT finish() pour éviter toute race condition
                                val snap    = sessionManager.session.value
                                val elapsed = sessionManager.elapsedMs.value
                                sessionManager.finish()
                                lifecycleScope.launch(Dispatchers.IO) {
                                    if (snap.lapCount > 0) {
                                        val savedId = sessionRepository.saveSession(snap, elapsed)
                                        Log.d("Room", "Session sauvegardée — id=$savedId laps=${snap.lapCount}")
                                        sessionRepository.getSessionById(savedId)?.let { entity ->
                                            WearSyncManager.sendSession(applicationContext, entity)
                                        }
                                    } else {
                                        Log.w("Room", "Session ignorée — 0 laps enregistrés")
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

                    // ── Historique ────────────────────────────────────────────
                    composable(Routes.HISTORY) {
                        var sessions by remember { mutableStateOf(listOf<com.piscine.timer.data.db.SessionEntity>()) }
                        LaunchedEffect(Unit) {
                            sessions = sessionRepository.getRecentSessions(20)
                        }
                        HistoryScreen(
                            sessions = sessions,
                            onBack   = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val app = application as PiscineTimerApp
        // Si session active et app envoyée en arrière-plan → ramener immédiatement
        val state = app.sessionManager.session.value.state
        if (state == SessionState.RUNNING) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }, 800L)
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as PiscineTimerApp
        val state = app.sessionManager.session.value.state
        if ((state == SessionState.RUNNING || state == SessionState.PAUSED) &&
            ::navController.isInitialized &&
            navController.currentDestination?.route != Routes.SWIMMING) {
            navController.navigate(Routes.SWIMMING) {
                popUpTo(Routes.READY) { inclusive = false }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentRoute = navController.currentDestination?.route
        if (currentRoute != Routes.SWIMMING) return super.onKeyDown(keyCode, event)
        val app = application as PiscineTimerApp
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_1, 294 -> {
                if (event?.repeatCount == 0) recordLapWithStrokes()
                true
            }
            KeyEvent.KEYCODE_STEM_2, 295 -> {
                app.sessionManager.finish()
                navController.navigate(Routes.SUMMARY)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        strokeCounter?.stop()
        spmCollectJob?.cancel()
        val app = application as PiscineTimerApp
        app.sessionManager.clear()
    }
}
