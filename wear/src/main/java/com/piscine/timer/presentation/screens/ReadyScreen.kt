package com.piscine.timer.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
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

/**
 * Écran d'accueil — sélection longueur bassin + démarrage.
 * Bouton ⚙ bien visible en bas à droite pour les paramètres.
 */
@Composable
fun ReadyScreen(
    onStart25m: () -> Unit,
    onStart50m: () -> Unit,
    onStartCustom: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    Scaffold(timeText = { TimeText() }) {
        Box(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text       = "🏊 PISCINE",
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Blue400,
                    textAlign  = TextAlign.Center
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text      = "Choisir le bassin",
                    fontSize  = 11.sp,
                    color     = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Bouton 25m
                Button(
                    onClick  = onStart25m,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    colors   = ButtonDefaults.buttonColors(backgroundColor = Blue400)
                ) {
                    Text("25 m", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(5.dp))

                // Bouton 50m
                Button(
                    onClick  = onStart50m,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    colors   = ButtonDefaults.buttonColors(backgroundColor = Cyan300)
                ) {
                    Text("50 m", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(5.dp))

                // Bouton longueur personnalisée
                Button(
                    onClick  = onStartCustom,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    colors   = ButtonDefaults.buttonColors(backgroundColor = Teal200)
                ) {
                    Text("Autre…", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Bouton ⚙ Paramètres — bas-centre, zone accessible sur écran rond
            CompactButton(
                onClick  = onSettings,
                colors   = ButtonDefaults.buttonColors(backgroundColor = Blue400),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .size(48.dp)
            ) {
                Text("⚙", fontSize = 18.sp)
            }
        }
    }
}
