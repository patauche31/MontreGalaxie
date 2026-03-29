package com.piscine.timer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.wear.ongoing.OngoingActivity
import com.piscine.timer.R
import com.piscine.timer.presentation.MainActivity

/**
 * Service en premier plan — garde la session active si l'app passe en arrière-plan.
 * Affiche un indicateur Ongoing Activity sur le cadran de la montre.
 */
class SwimTimerService : Service() {

    companion object {
        const val ACTION_START = "com.piscine.timer.START_SWIM"
        const val ACTION_STOP  = "com.piscine.timer.STOP_SWIM"
        const val ACTION_UPDATE = "com.piscine.timer.UPDATE_SWIM"
        const val EXTRA_ELAPSED = "elapsed_ms"
        const val EXTRA_LAPS    = "laps"
        const val NOTIFICATION_ID   = 42
        const val CHANNEL_ID        = "swim_timer_channel"
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PiscineTimer — Nage en cours",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> {
                val elapsed = intent.getLongExtra(EXTRA_ELAPSED, 0L)
                val laps    = intent.getIntExtra(EXTRA_LAPS, 0)
                showOngoingActivity(elapsed, laps)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

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
            .setContentTitle("PiscineTimer")
            .setContentText(statusText)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)

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

    override fun onBind(intent: Intent?): IBinder? = null
}
