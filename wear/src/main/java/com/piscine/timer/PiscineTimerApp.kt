package com.piscine.timer

import android.app.Application
import com.piscine.timer.data.PreferencesManager
import com.piscine.timer.domain.SensorLogger
import com.piscine.timer.domain.SessionManager

class PiscineTimerApp : Application() {
    lateinit var sessionManager: SessionManager
    lateinit var sensorLogger: SensorLogger
    lateinit var prefs: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        sensorLogger = SensorLogger(this)
        prefs = PreferencesManager(this)
        sessionManager = SessionManager(
            sensorLogger = if (prefs.debugLogging.value) sensorLogger else null
        )
    }
}
