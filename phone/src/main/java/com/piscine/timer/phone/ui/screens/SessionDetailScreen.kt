package com.piscine.timer.phone.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.piscine.timer.phone.data.db.SessionEntity
import com.piscine.timer.phone.ui.formatMs
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    session  : SessionEntity,
    onBack   : () -> Unit,
    onDelete : () -> Unit = {}
) {
    val context  = LocalContext.current
    val laps     = remember(session) { session.lapTimesMs() }
    val strokes  = remember(session) { session.strokeCountsPerLap() }
    val best     = laps.minOrNull() ?: 0L
    val worst    = laps.maxOrNull() ?: 0L

    // Métriques coups / SWOLF
    val hasStrokes   = strokes.isNotEmpty() && strokes.any { it > 0 }
    val avgStrokes   = remember(strokes) {
        if (strokes.isNotEmpty()) strokes.average().toInt() else 0
    }
    val swolfPerLap  = remember(laps, strokes) {
        if (hasStrokes && strokes.size == laps.size)
            laps.zip(strokes).map { (ms, s) -> if (s > 0) s + (ms / 1000).toInt() else 0 }
        else emptyList()
    }
    val avgSwolf     = remember(swolfPerLap) {
        val valid = swolfPerLap.filter { it > 0 }
        if (valid.isNotEmpty()) valid.average().toInt() else 0
    }
    val bestSwolf    = remember(swolfPerLap) { swolfPerLap.filter { it > 0 }.minOrNull() ?: 0 }
    val date    = remember(session.startTimestamp) {
        SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.FRANCE).format(Date(session.startTimestamp))
    }

    // Allure / 100m
    val pace100m = remember(session) {
        if (session.poolLengthMeters > 0 && session.averageLapTimeMs > 0)
            session.averageLapTimeMs * 100L / session.poolLengthMeters
        else 0L
    }

    // Dialog de confirmation suppression
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = Color(0xFF1A1A1A),
            title  = { Text("Supprimer cette séance ?", color = Color(0xFFE0E0E0)) },
            text   = { Text("Cette action est irréversible.", color = Color(0xFF757575)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Supprimer", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler", color = Color(0xFF4FC3F7))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détail séance", color = Color(0xFF4FC3F7)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color(0xFF4FC3F7))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val text = buildShareText(session, date, laps)
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }, "Partager"
                            )
                        )
                    }) {
                        Icon(Icons.Default.Share, null, tint = Color(0xFF4FC3F7))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFEF5350))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0D0D))
            )
        },
        containerColor = Color(0xFF0D0D0D)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Info date ─────────────────────────────────────────────────────
            Text(date, fontSize = 13.sp, color = Color(0xFF757575))

            // ── Stats principales ─────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape  = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("${session.totalDistanceMeters}m", "Distance",  Color(0xFF66BB6A))
                        StatItem(formatMs(session.totalTimeMs),      "Total",     Color(0xFF4FC3F7))
                        StatItem("${session.lapCount}",              "Longueurs", Color(0xFF4FC3F7))
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem(formatMs(session.averageLapTimeMs), "Moyenne",      Color(0xFF4FC3F7))
                        StatItem(formatMs(best),                     "🥇 Meilleure", Color(0xFFFFB74D))
                        StatItem(formatMs(worst),                    "Moins bonne",  Color(0xFFEF5350))
                    }
                    if (pace100m > 0L) {
                        HorizontalDivider(color = Color(0xFF2A2A2A))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            StatItem(formatMs(pace100m), "Allure / 100m", Color(0xFF9575CD))
                        }
                    }
                    if (hasStrokes) {
                        HorizontalDivider(color = Color(0xFF2A2A2A))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem("$avgStrokes",   "Coups/long",  Color(0xFF4DB6AC))
                            StatItem("$avgSwolf",     "SWOLF moy.",  Color(0xFF80CBC4))
                            StatItem("$bestSwolf",    "🏅 Best SWOLF", Color(0xFFFFCC02))
                        }
                    }
                }
            }

            // ── Graphique ─────────────────────────────────────────────────────
            if (laps.isNotEmpty()) {
                Text(
                    "Temps par longueur",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color(0xFFE0E0E0)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(8.dp),
                        factory  = { ctx ->
                            BarChart(ctx).apply {
                                description.isEnabled = false
                                legend.isEnabled      = false
                                setDrawGridBackground(false)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                                xAxis.apply {
                                    position       = XAxis.XAxisPosition.BOTTOM
                                    textColor      = android.graphics.Color.GRAY
                                    gridColor      = android.graphics.Color.parseColor("#222222")
                                    valueFormatter = IndexAxisValueFormatter(
                                        laps.indices.map { "L${it + 1}" }
                                    )
                                    granularity = 1f
                                    setDrawAxisLine(false)
                                }
                                axisLeft.apply {
                                    textColor   = android.graphics.Color.GRAY
                                    gridColor   = android.graphics.Color.parseColor("#222222")
                                    axisMinimum = (laps.min() / 1000f - 3f).coerceAtLeast(0f)
                                    setDrawAxisLine(false)
                                }
                                axisRight.isEnabled = false

                                val entries = laps.mapIndexed { i, ms ->
                                    BarEntry(i.toFloat(), ms / 1000f)
                                }
                                val barColors = laps.map { ms ->
                                    when (ms) {
                                        best  -> android.graphics.Color.parseColor("#FFB74D")
                                        worst -> android.graphics.Color.parseColor("#EF5350")
                                        else  -> android.graphics.Color.parseColor(
                                            if (ms <= session.averageLapTimeMs) "#4DB6AC" else "#4FC3F7"
                                        )
                                    }
                                }
                                data = BarData(
                                    BarDataSet(entries, "Temps").apply {
                                        this.colors    = barColors
                                        valueTextColor = android.graphics.Color.TRANSPARENT
                                    }
                                )
                                animateY(600)
                                invalidate()
                            }
                        }
                    )
                }
            }

            // ── Graphique SWOLF ───────────────────────────────────────────────
            if (swolfPerLap.any { it > 0 }) {
                Text(
                    "SWOLF par longueur",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color(0xFFE0E0E0)
                )
                Text(
                    "Plus bas = plus efficace  (coups + secondes)",
                    fontSize = 11.sp,
                    color    = Color(0xFF757575),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(8.dp),
                        factory  = { ctx ->
                            BarChart(ctx).apply {
                                description.isEnabled = false
                                legend.isEnabled      = false
                                setDrawGridBackground(false)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                                xAxis.apply {
                                    position       = XAxis.XAxisPosition.BOTTOM
                                    textColor      = android.graphics.Color.GRAY
                                    gridColor      = android.graphics.Color.parseColor("#222222")
                                    valueFormatter = IndexAxisValueFormatter(
                                        swolfPerLap.indices.map { "L${it + 1}" }
                                    )
                                    granularity = 1f
                                    setDrawAxisLine(false)
                                }
                                axisLeft.apply {
                                    textColor   = android.graphics.Color.GRAY
                                    gridColor   = android.graphics.Color.parseColor("#222222")
                                    val validSwolf = swolfPerLap.filter { it > 0 }
                                    axisMinimum = ((validSwolf.minOrNull() ?: 0) - 5f).coerceAtLeast(0f)
                                    setDrawAxisLine(false)
                                }
                                axisRight.isEnabled = false

                                val entries = swolfPerLap.mapIndexed { i, sw ->
                                    BarEntry(i.toFloat(), sw.toFloat())
                                }
                                val bestSw = swolfPerLap.filter { it > 0 }.minOrNull() ?: 0
                                val barColors = swolfPerLap.map { sw ->
                                    when {
                                        sw == bestSw -> android.graphics.Color.parseColor("#FFCC02")
                                        sw == 0      -> android.graphics.Color.TRANSPARENT
                                        sw <= avgSwolf -> android.graphics.Color.parseColor("#4DB6AC")
                                        else           -> android.graphics.Color.parseColor("#80CBC4")
                                    }
                                }
                                data = BarData(
                                    BarDataSet(entries, "SWOLF").apply {
                                        this.colors    = barColors
                                        valueTextColor = android.graphics.Color.TRANSPARENT
                                    }
                                )
                                animateY(600)
                                invalidate()
                            }
                        }
                    )
                }
            }

            // ── Tableau des longueurs ─────────────────────────────────────────
            if (laps.isNotEmpty()) {
                Text(
                    "Détail longueurs",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color(0xFFE0E0E0)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        // En-tête
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("",        fontSize = 12.sp, color = Color(0xFF424242), modifier = Modifier.width(32.dp))
                            Text("Temps",   fontSize = 12.sp, color = Color(0xFF424242), modifier = Modifier.width(64.dp))
                            Text("vs moy.", fontSize = 12.sp, color = Color(0xFF424242), modifier = Modifier.width(64.dp))
                            Text("",        fontSize = 12.sp, color = Color(0xFF424242))
                        }
                        HorizontalDivider(color = Color(0xFF2A2A2A))

                        laps.forEachIndexed { i, ms ->
                            val isBest    = ms == best
                            val isWorst   = ms == worst && ms != best
                            val lapStrokes = strokes.getOrNull(i) ?: 0

                            // Delta vs moyenne
                            val delta    = ms - session.averageLapTimeMs
                            val deltaAbs = abs(delta)
                            val deltaStr = when {
                                deltaAbs < 100L -> "≈ moy"
                                delta < 0       -> "-${formatMs(deltaAbs)}"
                                else            -> "+${formatMs(deltaAbs)}"
                            }
                            val deltaColor = when {
                                deltaAbs < 100L -> Color(0xFF757575)
                                delta < 0       -> Color(0xFF66BB6A)
                                else            -> Color(0xFFEF5350)
                            }

                            // SWOLF = coups + secondes
                            val swolf = if (lapStrokes > 0)
                                lapStrokes + (ms / 1000).toInt() else 0

                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "L${i + 1}",
                                        color    = Color(0xFF757575),
                                        fontSize = 13.sp,
                                        modifier = Modifier.width(32.dp)
                                    )
                                    Text(
                                        formatMs(ms),
                                        color      = when {
                                            isBest  -> Color(0xFFFFB74D)
                                            isWorst -> Color(0xFFEF5350)
                                            else    -> Color(0xFFE0E0E0)
                                        },
                                        fontWeight = if (isBest || isWorst) FontWeight.Bold
                                                     else FontWeight.Normal,
                                        fontSize   = 14.sp,
                                        modifier   = Modifier.width(64.dp)
                                    )
                                    Text(
                                        deltaStr,
                                        color    = deltaColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(64.dp)
                                    )
                                    Text(
                                        if (isBest) "🥇" else if (isWorst) "⚠️" else "",
                                        fontSize = 14.sp
                                    )
                                }
                                // Coups + SWOLF si mesurés
                                if (lapStrokes > 0) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 32.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "$lapStrokes coups",
                                            fontSize = 11.sp,
                                            color    = Color(0xFF4DB6AC)
                                        )
                                        Text(
                                            "SWOLF $swolf",
                                            fontSize = 11.sp,
                                            color    = Color(0xFF757575),
                                            modifier = Modifier.width(64.dp)
                                        )
                                        Spacer(Modifier.width(32.dp))
                                    }
                                }
                            }
                            if (i < laps.size - 1) HorizontalDivider(color = Color(0xFF2A2A2A))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = Color(0xFF757575))
    }
}

private fun buildShareText(session: SessionEntity, date: String, laps: List<Long>): String {
    val best    = laps.minOrNull() ?: 0L
    val strokes = session.strokeCountsPerLap()
    val avgStr  = if (strokes.isNotEmpty()) strokes.average().toInt() else 0
    val avgSwolf = if (strokes.isNotEmpty() && strokes.size == laps.size)
        laps.zip(strokes).map { (ms, s) -> if (s > 0) s + (ms / 1000).toInt() else 0 }
            .filter { it > 0 }.average().toInt()
    else 0

    return buildString {
        appendLine("🏊 Séance du $date")
        appendLine("Bassin ${session.poolLengthMeters}m · ${session.lapCount} longueurs · ${session.totalDistanceMeters}m")
        appendLine("⏱ Total : ${formatMs(session.totalTimeMs)}")
        appendLine("📊 Moy/long : ${formatMs(session.averageLapTimeMs)}")
        if (best > 0) appendLine("🥇 Meilleure : ${formatMs(best)} (L${laps.indexOf(best) + 1})")
        if (avgStr > 0) appendLine("💪 ~$avgStr coups/long · SWOLF moy. $avgSwolf")
        appendLine()
        appendLine("Via PiscineTimer 🏊‍♂️")
    }.trimEnd()
}
