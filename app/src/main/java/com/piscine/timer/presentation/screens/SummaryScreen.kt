package com.piscine.timer.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import com.piscine.timer.domain.model.LapData
import com.piscine.timer.domain.model.LapData.Companion.formatTime
import com.piscine.timer.domain.model.SwimSession
import com.piscine.timer.presentation.theme.*
import android.util.Base64
import com.piscine.timer.presentation.util.QrCodeImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Récapitulatif de fin de session — scrollable avec la couronne de la montre.
 */
@Composable
fun SummaryScreen(
    session: SwimSession,
    totalElapsedMs: Long,
    onNewSession: () -> Unit
) {
    val bestLap  = session.bestLap
    val worstLap = session.worstLap

    // Contenu encodé dans le QR code
    val qrContent = buildQrContent(session, totalElapsedMs)

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 32.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── En-tête ──────────────────────────────────────────────────────────
        item {
            Text(
                text = "RÉCAP SESSION",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Blue400,
                textAlign = TextAlign.Center
            )
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        // ── Stats globales ───────────────────────────────────────────────────
        item {
            StatRow(label = "Durée totale", value = formatTime(totalElapsedMs))
        }
        item {
            StatRow(label = "Longueurs", value = "${session.lapCount}")
        }
        item {
            StatRow(label = "Distance", value = "${session.totalDistanceMeters} m")
        }
        item {
            StatRow(label = "Moy / long.", value = session.averageLapTimeFormatted, color = Cyan300)
        }

        if (bestLap != null) {
            item {
                StatRow(
                    label = "Meilleure #${bestLap.lapNumber}",
                    value = bestLap.lapTimeFormatted,
                    color = Amber400
                )
            }
        }
        if (worstLap != null) {
            item {
                StatRow(
                    label = "Moins bonne #${worstLap.lapNumber}",
                    value = worstLap.lapTimeFormatted,
                    color = Red400
                )
            }
        }

        // ── Cadence moyenne (si coups mesurés) ──────────────────────────────
        val lapsWithStrokes = session.laps.filter { it.strokeCount > 0 }
        if (lapsWithStrokes.isNotEmpty()) {
            val avgStrokes = lapsWithStrokes.map { it.strokeCount }.average()
            item {
                StatRow(
                    label = "Moy. coups/long.",
                    value = "%.1f".format(avgStrokes),
                    color = Teal200
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // ── Détail longueur par longueur ────────────────────────────────────
        item {
            Text(
                text = "Détail",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
            )
        }

        items(session.laps) { lap ->
            LapRow(lap = lap, poolMeters = session.effectivePoolMeters, isBest = lap == bestLap, isWorst = lap == worstLap)
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // ── QR Code — scannez avec l'iPhone ─────────────────────────────────
        item {
            Text(
                text = "QR Code → iPhone",
                fontSize = 11.sp,
                color = Teal200,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        item {
            QrCodeImage(
                content = qrContent,
                size = 190.dp
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // ── Bouton Nouvelle session ──────────────────────────────────────────
        item {
            Button(
                onClick = onNewSession,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = Blue400)
            ) {
                Text(
                    text = "Nouvelle session",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Formatage du contenu QR code ─────────────────────────────────────────────

// IP du PC sur le hotspot Windows (toujours 192.168.137.1)
private const val DASHBOARD_URL = "https://patauche31.github.io/piscine-timer"

private fun buildQrContent(session: SwimSession, totalElapsedMs: Long): String {
    val date = SimpleDateFormat("dd/MM/yy HH:mm", Locale.FRANCE).format(Date())

    // JSON compact encodé en base64 pour l'URL
    val lapsJson = session.laps.joinToString(",") { it.lapTimeMs.toString() }
    val json = buildString {
        append("{")
        append("\"d\":\"$date\",")
        append("\"p\":\"${session.effectivePoolMeters}\",")
        append("\"n\":${session.lapCount},")
        append("\"t\":$totalElapsedMs,")
        append("\"avg\":${session.averageLapTimeMs},")
        append("\"laps\":[$lapsJson]")
        append("}")
    }

    val b64 = Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    return "$DASHBOARD_URL/?s=$b64"
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun LapRow(lap: LapData, poolMeters: Int, isBest: Boolean, isWorst: Boolean) {
    val textColor = when {
        isBest  -> Amber400
        isWorst -> Red400
        else    -> White
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text  = "#${lap.lapNumber}  ${lap.distanceMeters(poolMeters)}m",
                fontSize = 11.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
            )
            Text(
                text       = lap.lapTimeFormatted,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = textColor
            )
        }
        // Coups + SWOLF sur la ligne du dessous (si mesurés)
        if (lap.strokeCount > 0) {
            val swolf = lap.swolf(poolMeters) ?: 0
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text     = "${lap.strokeCount} coups",
                    fontSize = 10.sp,
                    color    = Teal200
                )
                Text(
                    text     = "SWOLF $swolf",
                    fontSize = 10.sp,
                    color    = MaterialTheme.colors.onBackground.copy(alpha = 0.45f)
                )
            }
        }
    }
}
