package com.piscine.timer.presentation.screens

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Interactions tactiles :
 *   - Tap zone centrale  → recordLap()
 *   - Bouton ⏸ (bas gauche) → togglePause()
 *   - Bouton ■ (bas droite)  → finish()
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
    val isPaused = session.state == SessionState.PAUSED
    val lastLap: LapData? = session.laps.lastOrNull()
    val context = LocalContext.current
    val vibrator = remember {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Zone centrale cliquable = LAP ────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
                    .clickable(
                        enabled           = !isPaused && lapDetector?.isInLockout != true,
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (vibrationEnabled) {
                            vibrator.vibrate(
                                VibrationEffect.createWaveform(
                                    longArrayOf(0, 80, 60, 80), -1
                                )
                            )
                        }
                        onLap()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {

                    // ── Ligne 1 : numéro longueur + distance ─────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = "Long. ${session.lapCount + 1}",
                            fontSize   = 13.sp,
                            color      = Cyan300,
                            fontWeight = FontWeight.Medium
                        )
                        if (isPaused) {
                            Text(
                                text       = "PAUSE",
                                fontSize   = 12.sp,
                                color      = Amber400,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text  = "${session.totalDistanceMeters}m",
                                fontSize = 13.sp,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // ── Ligne 1b : style détecté + AUTO ──────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        if (swimStyle != SwimStyle.INCONNU) {
                            Text(
                                text       = swimStyle.label,
                                fontSize   = 12.sp,
                                color      = when (swimStyle) {
                                    SwimStyle.CRAWL    -> Blue400
                                    SwimStyle.BRASSE   -> Teal200
                                    SwimStyle.PAPILLON -> Amber400
                                    else               -> MaterialTheme.colors.onBackground
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (autoDetectActive) {
                            Text(
                                text       = "AUTO",
                                fontSize   = 10.sp,
                                color      = Teal200,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // ── Ligne 2 : chrono total ───────────────────────────────
                    Text(
                        text          = formatTime(elapsedMs),
                        fontSize      = 36.sp,
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

                    // ── Ligne 4 : cadence SPM OU dernier passage ─────────────
                    if (currentSpm >= 20f && !isPaused) {
                        // Cadence mesurée : affiche c/min en vert
                        Text(
                            text      = "${currentSpm.toInt()} c/min",
                            fontSize  = 11.sp,
                            color     = Teal200,
                            textAlign = TextAlign.Center,
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
                        Text(
                            text     = if (autoDetectActive) "virage auto 🔄" else "TAP = passage",
                            fontSize = 11.sp,
                            color    = if (autoDetectActive)
                                           Teal200.copy(alpha = 0.6f)
                                       else
                                           MaterialTheme.colors.onBackground.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── Boutons bas : Pause | Stop (centrés, loin des bords courbes) ──
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                CompactButton(
                    onClick = onTogglePause,
                    colors  = ButtonDefaults.buttonColors(
                        backgroundColor = if (isPaused) Teal200 else Amber400
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Text(text = if (isPaused) "▶" else "⏸", fontSize = 18.sp)
                }

                CompactButton(
                    onClick  = onFinish,
                    colors   = ButtonDefaults.buttonColors(backgroundColor = Red400),
                    modifier = Modifier.size(52.dp)
                ) {
                    Text(text = "■", fontSize = 18.sp)
                }
            }
        }
    }
}
