package com.piscine.timer.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.piscine.timer.data.PreferencesManager
import com.piscine.timer.presentation.theme.Blue400
import com.piscine.timer.presentation.theme.Cyan300
import com.piscine.timer.presentation.theme.Teal200

/**
 * Écran Paramètres de l'app montre.
 * Accessible depuis ReadyScreen (icône ⚙).
 */
@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    onBack: () -> Unit
) {
    val vibration   by prefs.vibrationEnabled.collectAsState()
    val autoDetect  by prefs.autoDetectEnabled.collectAsState()
    val debugLog    by prefs.debugLogging.collectAsState()

    Scaffold(
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── Titre ─────────────────────────────────────────────────────────
            item {
                Text(
                    text       = "⚙ Paramètres",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Blue400,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.padding(bottom = 4.dp)
                )
            }

            // ── Vibration ─────────────────────────────────────────────────────
            item {
                ToggleChip(
                    checked        = vibration,
                    onCheckedChange = { prefs.setVibrationEnabled(it) },
                    label = {
                        Text(
                            "Vibration",
                            fontSize = 14.sp,
                            color    = MaterialTheme.colors.onSurface
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text     = if (vibration) "Active au passage" else "Désactivée",
                            fontSize = 11.sp,
                            color    = if (vibration) Teal200
                                       else MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    toggleControl = {
                        Switch(
                            checked = vibration,
                            modifier = Modifier.size(width = 32.dp, height = 18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Détection automatique ─────────────────────────────────────────
            item {
                ToggleChip(
                    checked        = autoDetect,
                    onCheckedChange = { prefs.setAutoDetectEnabled(it) },
                    label = {
                        Text(
                            "Détection auto",
                            fontSize = 14.sp,
                            color    = MaterialTheme.colors.onSurface
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text     = if (autoDetect) "Gyroscope activé" else "Manuel uniquement",
                            fontSize = 11.sp,
                            color    = if (autoDetect) Cyan300
                                       else MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    toggleControl = {
                        Switch(
                            checked = autoDetect,
                            modifier = Modifier.size(width = 32.dp, height = 18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Debug capteurs (CSV) ──────────────────────────────────────────
            item {
                ToggleChip(
                    checked         = debugLog,
                    onCheckedChange = { prefs.setDebugLogging(it) },
                    label = {
                        Text("Log capteurs", fontSize = 14.sp,
                            color = MaterialTheme.colors.onSurface)
                    },
                    secondaryLabel = {
                        Text(
                            text     = if (debugLog) "CSV → récup. ADB" else "Désactivé",
                            fontSize = 11.sp,
                            color    = if (debugLog) Teal200
                                       else MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    toggleControl = {
                        Switch(checked = debugLog,
                            modifier = Modifier.size(width = 32.dp, height = 18.dp))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Bouton retour ─────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                CompactButton(
                    onClick = onBack,
                    colors  = ButtonDefaults.buttonColors(backgroundColor = Blue400),
                    modifier = Modifier.size(44.dp)
                ) {
                    Text("←", fontSize = 18.sp)
                }
            }
        }
    }
}
