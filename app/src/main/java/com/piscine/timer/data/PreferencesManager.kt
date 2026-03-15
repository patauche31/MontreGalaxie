package com.piscine.timer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestionnaire de préférences utilisateur (SharedPreferences).
 * Expose des StateFlows réactifs pour l'UI Compose.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("piscine_prefs", Context.MODE_PRIVATE)

    // ── Vibration lors d'un passage ──────────────────────────────────────────
    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION, true))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
        _vibrationEnabled.value = enabled
    }

    // ── Détection automatique des virages ────────────────────────────────────
    private val _autoDetectEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DETECT, true))
    val autoDetectEnabled: StateFlow<Boolean> = _autoDetectEnabled.asStateFlow()

    fun setAutoDetectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DETECT, enabled).apply()
        _autoDetectEnabled.value = enabled
    }

    // ── Mode debug capteurs (CSV) ────────────────────────────────────────────
    private val _debugLogging = MutableStateFlow(prefs.getBoolean(KEY_DEBUG_LOG, false))
    val debugLogging: StateFlow<Boolean> = _debugLogging.asStateFlow()

    fun setDebugLogging(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_LOG, enabled).apply()
        _debugLogging.value = enabled
    }

    // ── Dernière longueur personnalisée ──────────────────────────────────────
    val lastCustomMeters: Int
        get() = prefs.getInt(KEY_CUSTOM_METERS, 33)

    fun setLastCustomMeters(meters: Int) {
        prefs.edit().putInt(KEY_CUSTOM_METERS, meters).apply()
    }

    companion object {
        private const val KEY_VIBRATION     = "vibration_enabled"
        private const val KEY_AUTO_DETECT   = "auto_detect_enabled"
        private const val KEY_CUSTOM_METERS = "last_custom_meters"
        private const val KEY_DEBUG_LOG     = "debug_logging"
    }
}
