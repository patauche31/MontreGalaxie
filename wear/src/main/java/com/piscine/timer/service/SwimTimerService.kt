package com.piscine.timer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.wear.ongoing.OngoingActivity
import com.piscine.timer.PiscineTimerApp
import com.piscine.timer.R
import com.piscine.timer.domain.LapDetector
import com.piscine.timer.domain.model.PoolLength
import com.piscine.timer.domain.model.SessionState
import com.piscine.timer.presentation.MainActivity

/**
 * Service en premier plan — garde la session active si l'app passe en arrière-plan.
 * Affiche un indicateur Ongoing Activity sur le cadran de la montre.
 * Contient aussi le LapDetector pour que la détection auto fonctionne en arrière-plan.
 */
class SwimTimerService : Service() {

    /** WakeLock écran — garde l'écran allumé pendant toute la session de nage */
    private var wakeLock: PowerManager.WakeLock? = null

    /** WakeLock CPU — maintient le CPU actif même écran éteint pour la détection de lap */
    private var partialWakeLock: PowerManager.WakeLock? = null

    /** Détecteur automatique de virages — tourne dans le service */
    private var lapDetector: LapDetector? = null

    companion object {
        const val ACTION_START       = "com.piscine.timer.START_SWIM"
        const val ACTION_STOP        = "com.piscine.timer.STOP_SWIM"
        const val ACTION_UPDATE      = "com.piscine.timer.UPDATE_SWIM"
        const val EXTRA_ELAPSED      = "elapsed_ms"
        const val EXTRA_LAPS         = "laps"
        const val EXTRA_POOL_LENGTH  = "pool_length"
        const val NOTIFICATION_ID    = 42
        const val CHANNEL_ID         = "swim_timer_channel"
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PiscineTimer — Nage en cours",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setShowBadge(true)
            setSound(null, null)      // pas de son
            enableVibration(false)    // pas de vibration sur la notif
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val elapsed    = intent.getLongExtra(EXTRA_ELAPSED, 0L)
                val laps       = intent.getIntExtra(EXTRA_LAPS, 0)
                val poolName   = intent.getStringExtra(EXTRA_POOL_LENGTH)
                acquireWakeLocks()
                showOngoingActivity(elapsed, laps)
                startLapDetector(poolName)
            }
            ACTION_UPDATE -> {
                val elapsed = intent.getLongExtra(EXTRA_ELAPSED, 0L)
                val laps    = intent.getIntExtra(EXTRA_LAPS, 0)
                showOngoingActivity(elapsed, laps)
            }
            ACTION_STOP -> {
                stopLapDetector()
                releaseWakeLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // ── LapDetector ───────────────────────────────────────────────────────────

    private fun startLapDetector(poolLengthName: String?) {
        // Si déjà actif, ne pas recréer
        if (lapDetector != null) return

        val app = application as PiscineTimerApp
        val prefs = app.prefs
        if (!prefs.autoDetectEnabled.value) return

        val poolLength = poolLengthName?.let {
            try { PoolLength.valueOf(it) } catch (e: IllegalArgumentException) { null }
        } ?: return

        lapDetector = LapDetector(
            context       = this,
            poolLength    = poolLength,
            logger        = if (prefs.debugLogging.value) app.sensorLogger else null,
            onLapDetected = {
                Log.d("SwimTimerService", "Virage détecté auto (service) !")
                app.sessionManager.recordLap(0)
            }
        )
        lapDetector?.start()
        Log.d("SwimTimerService", "LapDetector démarré dans le service (pool=$poolLength)")
    }

    private fun stopLapDetector() {
        lapDetector?.stop()
        lapDetector = null
        Log.d("SwimTimerService", "LapDetector arrêté")
    }

    // ── Wake locks ────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun acquireWakeLocks() {
        val pm = getSystemService(PowerManager::class.java)

        if (wakeLock?.isHeld != true) {
            wakeLock = pm.newWakeLock(
                // SCREEN_BRIGHT_WAKE_LOCK : garde l'écran allumé même en plongeant le poignet
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "PiscineTimer::SwimScreenLock"
            ).also { it.acquire(3 * 60 * 60 * 1000L) } // max 3 h
        }

        if (partialWakeLock?.isHeld != true) {
            partialWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PiscineTimer::SwimCpuLock"
            ).also { it.acquire(3 * 60 * 60 * 1000L) } // max 3 h
        }
    }

    private fun releaseWakeLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        partialWakeLock?.let { if (it.isHeld) it.release() }
        partialWakeLock = null
    }

    // ── Notification Ongoing Activity ─────────────────────────────────────────

    private fun showOngoingActivity(elapsedMs: Long, laps: Int) {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsedSec = elapsedMs / 1000
        val mm = elapsedSec / 60
        val ss = elapsedSec % 60
        val timeStr = "%02d:%02d".format(mm, ss)
        val statusText = "$timeStr  •  Long. $laps"

        val notifBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🏊 Nage en cours — $statusText")
            .setContentText("Appuie pour revenir")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val ongoingActivity = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notifBuilder)
            .setTouchIntent(tapIntent)
            .build()

        ongoingActivity.apply(applicationContext)

        // Android 14+ exige le type dans startForeground
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notifBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else
                0
        )
    }

    override fun onDestroy() {
        stopLapDetector()
        releaseWakeLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
