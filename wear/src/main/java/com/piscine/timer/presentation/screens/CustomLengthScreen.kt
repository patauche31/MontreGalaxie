package com.piscine.timer.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.piscine.timer.presentation.theme.Blue400
import com.piscine.timer.presentation.theme.Cyan300
import com.piscine.timer.presentation.theme.Teal200

private const val MIN_METERS = 10
private const val MAX_METERS = 200

/**
 * Écran de saisie d'une longueur de bassin personnalisée.
 * Boutons +1/-1 et +5/-5 pour ajuster rapidement.
 */
@Composable
fun CustomLengthScreen(
    initialMeters: Int = 33,
    onStart: (meters: Int) -> Unit,
    onBack: () -> Unit
) {
    var meters by remember { mutableStateOf(initialMeters.coerceIn(MIN_METERS, MAX_METERS)) }

    Scaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text       = "Longueur bassin",
                fontSize   = 13.sp,
                color      = Cyan300,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // ── Ajustement ±5 ────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // -5
                CompactButton(
                    onClick  = { meters = (meters - 5).coerceAtLeast(MIN_METERS) },
                    colors   = ButtonDefaults.buttonColors(backgroundColor = Blue400.copy(alpha = 0.6f)),
                    modifier = Modifier.size(36.dp)
                ) { Text("-5", fontSize = 11.sp) }

                // -1
                CompactButton(
                    onClick  = { meters = (meters - 1).coerceAtLeast(MIN_METERS) },
                    colors   = ButtonDefaults.buttonColors(backgroundColor = Blue400),
                    modifier = Modifier.size(36.dp)
                ) { Text("-1", fontSize = 11.sp) }

                // Valeur centrale
                Text(
                    text       = "$meters m",
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Teal200,
                    modifier   = Modifier.width(80.dp),
                    textAlign  = TextAlign.Center
                )

                // +1
                CompactButton(
                    onClick  = { meters = (meters + 1).coerceAtMost(MAX_METERS) },
                    colors   = ButtonDefaults.buttonColors(backgroundColor = Blue400),
                    modifier = Modifier.size(36.dp)
                ) { Text("+1", fontSize = 11.sp) }

                // +5
                CompactButton(
                    onClick  = { meters = (meters + 5).coerceAtMost(MAX_METERS) },
                    colors   = ButtonDefaults.buttonColors(backgroundColor = Blue400.copy(alpha = 0.6f)),
                    modifier = Modifier.size(36.dp)
                ) { Text("+5", fontSize = 11.sp) }
            }

            Spacer(Modifier.height(12.dp))

            // ── Bouton Démarrer ───────────────────────────────────────────────
            Button(
                onClick  = { onStart(meters) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors   = ButtonDefaults.buttonColors(backgroundColor = Teal200)
            ) {
                Text("▶ Démarrer", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(6.dp))

            // ── Retour ────────────────────────────────────────────────────────
            CompactButton(
                onClick  = onBack,
                colors   = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.4f)
                ),
                modifier = Modifier.size(32.dp)
            ) { Text("←", fontSize = 14.sp) }
        }
    }
}
