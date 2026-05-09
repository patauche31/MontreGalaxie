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
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.*
import com.piscine.timer.data.db.SessionEntity
import com.piscine.timer.domain.model.LapData.Companion.formatTime
import com.piscine.timer.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Écran historique des sessions — scrollable avec la couronne.
 * Affiche les 20 dernières sessions avec stats clés.
 */
@Composable
fun HistoryScreen(
    sessions: List<SessionEntity>,
    onBack: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 32.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        // ── Titre ────────────────────────────────────────────────────────────
        item {
            Text(
                text       = "📋 HISTORIQUE",
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                color      = Blue400,
                textAlign  = TextAlign.Center
            )
        }

        if (sessions.isEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    text      = "Aucune session enregistrée",
                    fontSize  = 12.sp,
                    color     = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // ── Résumé global ────────────────────────────────────────────────
            val totalDist = sessions.sumOf { it.totalDistanceMeters }
            val totalTime = sessions.sumOf { it.totalTimeMs }

            item {
                Card(
                    onClick  = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier            = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text      = "${sessions.size} sessions",
                            fontSize  = 12.sp,
                            color     = Cyan300,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text     = "${totalDist} m au total",
                            fontSize = 11.sp,
                            color    = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                        )
                        Text(
                            text     = formatTime(totalTime),
                            fontSize = 11.sp,
                            color    = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(2.dp)) }

            // ── Liste des sessions ────────────────────────────────────────────
            itemsIndexed(sessions) { index, session ->
                SessionCard(
                    session    = session,
                    index      = index + 1,
                    dateStr    = dateFormat.format(Date(session.startTimestamp))
                )
            }
        }

        item { Spacer(Modifier.height(4.dp)) }

        // ── Bouton retour ────────────────────────────────────────────────────
        item {
            CompactButton(
                onClick  = onBack,
                colors   = ButtonDefaults.buttonColors(backgroundColor = Blue400),
                modifier = Modifier.size(44.dp)
            ) {
                Text("←", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionEntity,
    index: Int,
    dateStr: String
) {
    val lapTimes = if (session.lapTimesJson.isNotBlank())
        session.lapTimesJson.split(",").mapNotNull { it.trim().toLongOrNull() }
    else emptyList()

    val bestMs = lapTimes.minOrNull()

    Card(
        onClick  = {},
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {

            // Ligne 1 : numéro + date
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text      = "#$index",
                    fontSize  = 11.sp,
                    color     = Blue400,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text     = dateStr,
                    fontSize = 10.sp,
                    color    = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(Modifier.height(2.dp))

            // Ligne 2 : distance + longueurs
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text      = "${session.lapCount}×${session.poolLengthMeters}m",
                    fontSize  = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color     = Cyan300
                )
                Text(
                    text     = "${session.totalDistanceMeters} m",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color    = White
                )
            }

            Spacer(Modifier.height(2.dp))

            // Ligne 3 : durée + moyenne
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text     = "⏱ ${formatTime(session.totalTimeMs)}",
                    fontSize = 11.sp,
                    color    = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text     = "moy ${formatTime(session.averageLapTimeMs)}",
                    fontSize = 11.sp,
                    color    = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }

            // Ligne 4 : meilleure longueur (si dispo)
            if (bestMs != null) {
                Text(
                    text     = "🏅 ${formatTime(bestMs)}  (#${session.bestLapNumber})",
                    fontSize = 10.sp,
                    color    = Amber400,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
