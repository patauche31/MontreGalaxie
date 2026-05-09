package com.piscine.timer.presentation.screens

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.piscine.timer.domain.LapDetector
import com.piscine.timer.domain.model.LapData
import com.piscine.timer.domain.model.LapData.Companion.formatTime
import com.piscine.timer.domain.model.SessionState
import com.piscine.timer.domain.model.SwimSession
import com.piscine.timer.domain.model.SwimStyle
import com.piscine.timer.presentation.theme.*

/**
 * Écran principal pendant la nage.
 *
 * RUNNING  : écran verrouillé (eau ne peut rien déclencher)
 *            → appui long n'importe où = Pause
 *            → détection auto = compte les virages + vibration
 *
 * PAUSED   : écran normal
 *            → tap ▶ = reprendre
 *            → tap ■ = terminer (confirmation)
 */
@Composable
fun SwimmingScreen(
    session          : SwimSession,
    elapsedMs        : Long,
    currentLapMs     : Long,
    autoDetectActive : Boolean      = false,
    vibrationEnabled : Boolean      = true,
    currentSpm       : Float        = 0f,
    swimStyle        : SwimStyle    = SwimStyle.INCONNU,
    lapDetector      : LapDetector? = null,
    onLap            : () -> Unit,
    onTogglePause    : () -> Unit,
    onFinish         : () -> Unit
) {
    val isPaused  = session.state == SessionState.PAUSED
    val isRunning = session.state == SessionState.RUNNING
    val lastLap: LapData? = session.laps.lastOrNull()
    var showFinishDialog by remember { mutableStateOf(false) }
    val context  = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Zone principale ──────────────────────────────────────────────
            // EN NAGE  : tap = passage  |  appui long = pause
            // EN PAUSE : pas d'action sur la zone centrale
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
                    .pointerInput(isRunning) {
                        if (isRunning) {
                            detectTapGestures(
                                onTap = {
                                    // Tap simple = compter une longueur
                                    onLap()
                                },
                                onLongPress = {
                                    // Appui long = basculer en pause — vibration longue distincte
                                    vibrator.vibrate(
                                        VibrationEffect.createWaveform(
                                            longArrayOf(0, 600),
                                            intArrayOf(0, 255),
                                            -1
                                        )
                                    )
                                    onTogglePause()
                                }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {

                    // ── Ligne 1 : COMPTEUR DE LONGUEURS (grand, permanent) ───
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.Bottom
                    ) {
                        Text(
                            text       = "${session.lapCount}",
                            fontSize   = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Cyan300,
                            textAlign  = TextAlign.Center
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text       = "long.",
                                fontSize   = 12.sp,
                                color      = Cyan300.copy(alpha = 0.7f),
                                modifier   = Modifier.padding(bottom = 6.dp)
                            )
                            if (isPaused) {
                                Text(
                                    text       = "PAUSE",
                                    fontSize   = 10.sp,
                                    color      = Amber400,
                                    fontWeight = FontWeight.Bold,
                                    modifier   = Modifier.padding(bottom = 6.dp)
                                )
                            } else if (autoDetectActive) {
                                Text(
                                    text     = "AUTO",
                                    fontSize = 10.sp,
                                    color    = Teal200,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            } else {
                                Text(
                                    text     = "${session.totalDistanceMeters}m",
                                    fontSize = 10.sp,
                                    color    = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                    }

                    // ── Ligne 2 : chrono total ───────────────────────────────
                    Text(
                        text          = formatTime(elapsedMs),
                        fontSize      = 28.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = if (isPaused) Amber400 else Blue400,
                        textAlign     = TextAlign.Center,
                        letterSpacing = 1.sp
                    )

                    // ── Ligne 3 : temps longueur en cours ───────────────────
                    Text(
                        text       = formatTime(currentLapMs),
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color      = White,
                        textAlign  = TextAlign.Center
                    )

                    // ── Ligne 4 : cadence / dernier passage / hint ───────────
                    if (currentSpm >= 20f && !isPaused) {
                        Text(
                            text       = "${currentSpm.toInt()} c/min",
                            fontSize   = 11.sp,
                            color      = Teal200,
                            textAlign  = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (lastLap != null) {
                        val strokeInfo = if (lastLap.strokeCount > 0)
                            "  ${lastLap.strokeCount}✋" else ""
                        Text(
                            text      = "Préc #${lastLap.lapNumber}  ${lastLap.lapTimeFormatted}$strokeInfo",
                            fontSize  = 11.sp,
                            color     = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Hint adapté au mode
                        Text(
                            text     = if (isRunning) "TAP = passage  •  long = pause" else "TAP = passage",
                            fontSize = 11.sp,
                            color    = MaterialTheme.colors.onBackground.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── Indicateur AUTO (EN NAGE) ────────────────────────────────────
            if (isRunning && autoDetectActive) {
                Text(
                    text     = "AUTO 🏊",
                    fontSize = 11.sp,
                    color    = Teal200.copy(alpha = 0.4f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                )
            }

            // ── Boutons (EN PAUSE uniquement) ────────────────────────────────
            if (isPaused) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // ▶ Reprendre
                    CompactButton(
                        onClick  = onTogglePause,
                        colors   = ButtonDefaults.buttonColors(backgroundColor = Teal200),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Text("▶", fontSize = 18.sp)
                    }

                    // ■ Terminer
                    CompactButton(
                        onClick  = { showFinishDialog = true },
                        colors   = ButtonDefaults.buttonColors(backgroundColor = Red400),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Text("■", fontSize = 18.sp)
                    }
                }
            }

            // ── Confirmation arrêt ────────────────────────────────────────────
            if (showFinishDialog) {
                Dialog(
                    showDialog       = showFinishDialog,
                    onDismissRequest = { showFinishDialog = false }
                ) {
                    Alert(
                        title = {
                            Text(
                                "Terminer ?",
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign  = TextAlign.Center
                            )
                        },
                        negativeButton = {
                            Button(
                                onClick = { showFinishDialog = false },
                                colors  = ButtonDefaults.buttonColors(
                                    backgroundColor = MaterialTheme.colors.surface
                                )
                            ) { Text("✕", fontSize = 16.sp) }
                        },
                        positiveButton = {
                            Button(
                                onClick = { showFinishDialog = false; onFinish() },
                                colors  = ButtonDefaults.buttonColors(backgroundColor = Red400)
                            ) { Text("■", fontSize = 16.sp) }
                        }
                    ) {
                        Text(
                            text      = "${session.lapCount} longueurs",
                            fontSize  = 12.sp,
                            color     = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
