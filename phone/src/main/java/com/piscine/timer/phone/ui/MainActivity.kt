package com.piscine.timer.phone.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.piscine.timer.phone.data.db.SessionEntity
import com.piscine.timer.phone.data.db.SwimDatabase
import com.piscine.timer.phone.ui.screens.SessionDetailScreen
import com.piscine.timer.phone.ui.viewmodel.SessionViewModel
import com.piscine.timer.phone.ui.viewmodel.SessionViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = SwimDatabase.getInstance(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val vm: SessionViewModel = viewModel(
                    factory = SessionViewModelFactory(db.sessionDao())
                )
                PiscineTimerApp(vm)
            }
        }
    }
}

@Composable
fun PiscineTimerApp(vm: SessionViewModel) {
    val sessions  by vm.sessions.collectAsStateWithLifecycle(emptyList())
    val totalDist by vm.totalDistance.collectAsStateWithLifecycle(0)
    val totalTime by vm.totalTime.collectAsStateWithLifecycle(null)
    val count     by vm.sessionCount.collectAsStateWithLifecycle(0)
    val best25    by vm.bestLap25m.collectAsStateWithLifecycle(null)
    val best50    by vm.bestLap50m.collectAsStateWithLifecycle(null)

    var selected    : SessionEntity? by remember { mutableStateOf(null) }
    var showAnalysis: Boolean        by remember { mutableStateOf(false) }
    var filterPool  : Int?           by remember { mutableStateOf(null) }

    if (selected != null) {
        SessionDetailScreen(
            session  = selected!!,
            onBack   = { selected = null },
            onDelete = { vm.deleteSession(selected!!.id); selected = null }
        )
        return
    }

    if (showAnalysis) {
        com.piscine.timer.phone.ui.screens.AnalysisScreen(
            sessions = sessions,
            onBack   = { showAnalysis = false }
        )
        return
    }

    val filteredSessions = remember(sessions, filterPool) {
        if (filterPool == null) sessions
        else sessions.filter { it.poolLengthMeters == filterPool }
    }
    val availablePools = remember(sessions) {
        sessions.map { it.poolLengthMeters }.distinct().sorted()
    }
    val chartSessions = remember(filteredSessions) {
        filteredSessions
            .filter { it.poolLengthMeters > 0 && it.averageLapTimeMs > 0 }
            .sortedBy { it.startTimestamp }
            .takeLast(20)
    }

    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding      = PaddingValues(top = 20.dp, bottom = 24.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text       = "🏊 PiscineTimer",
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFF4FC3F7)
                    )
                    Text(
                        text     = "Mes séances",
                        fontSize = 16.sp,
                        color    = Color(0xFF757575),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (sessions.size >= 2) {
                    Button(
                        onClick = { showAnalysis = true },
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A237E)
                        ),
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📊", fontSize = 20.sp)
                            Text("Analyser", fontSize = 11.sp, color = Color(0xFF90CAF9))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ── Stats — ligne 1 : Séances + Distance ─────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    value    = "$count",
                    label    = "Séances",
                    color    = Color(0xFF4FC3F7),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value    = "${totalDist ?: 0}m",
                    label    = "Distance totale",
                    color    = Color(0xFF66BB6A),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Stats — ligne 2 : Best 25m + Best 50m ────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    value    = best25?.let { formatMs(it) } ?: "—",
                    label    = "🥇 Best 25m",
                    color    = Color(0xFFFFB74D),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value    = best50?.let { formatMs(it) } ?: "—",
                    label    = "🥇 Best 50m",
                    color    = Color(0xFFFF8A65),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Temps total en eau ────────────────────────────────────────────
        totalTime?.let { ms ->
            if (ms > 0L) {
                item {
                    StatCard(
                        value    = formatTotalTime(ms),
                        label    = "⏱ Temps total en eau",
                        color    = Color(0xFF9575CD),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── Graphique progression ─────────────────────────────────────────
        if (chartSessions.size >= 2) {
            item { ProgressionChart(chartSessions) }
        }

        // ── Filtre bassin ─────────────────────────────────────────────────
        if (availablePools.size > 1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterPool == null,
                        onClick  = { filterPool = null },
                        label    = { Text("Tous", fontSize = 14.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1565C0),
                            selectedLabelColor     = Color.White
                        )
                    )
                    availablePools.forEach { pool ->
                        FilterChip(
                            selected = filterPool == pool,
                            onClick  = { filterPool = if (filterPool == pool) null else pool },
                            label    = { Text("${pool}m", fontSize = 14.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00695C),
                                selectedLabelColor     = Color.White
                            )
                        )
                    }
                }
            }
        }

        // ── Titre liste ───────────────────────────────────────────────────
        if (filteredSessions.isNotEmpty()) {
            item {
                Text(
                    text     = "Séances récentes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color    = Color(0xFFE0E0E0)
                )
            }
        }

        // ── Séances ou état vide ──────────────────────────────────────────
        if (filteredSessions.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⌚", fontSize = 56.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (sessions.isEmpty()) "En attente de la montre…"
                            else "Aucune séance en ${filterPool}m",
                            color      = Color(0xFF757575),
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (sessions.isEmpty()) {
                            Text(
                                "Lance une séance sur ta Galaxy Watch.\nLes résultats arrivent automatiquement.",
                                color      = Color(0xFF424242),
                                fontSize   = 14.sp,
                                modifier   = Modifier.padding(top = 10.dp),
                                lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = { vm.insertDemoSessions() },
                                border  = androidx.compose.foundation.BorderStroke(
                                    1.dp, Color(0xFF37474F)
                                ),
                                shape   = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    "🧪 Insérer données démo",
                                    color    = Color(0xFF546E7A),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredSessions, key = { it.id }) { session ->
                SessionCard(session = session, onClick = { selected = session })
            }
        }
    }
}

// ── Graphique progression ─────────────────────────────────────────────────────

@Composable
fun ProgressionChart(sessions: List<SessionEntity>) {
    val labels = remember(sessions) {
        val fmt = SimpleDateFormat("dd/MM", Locale.FRANCE)
        sessions.map { fmt.format(Date(it.startTimestamp)) }
    }
    val paceValues = remember(sessions) {
        sessions.map { s -> s.averageLapTimeMs * 100f / s.poolLengthMeters / 1000f }
    }
    val avgPace = remember(paceValues) { paceValues.average().toFloat() }

    Column {
        Text(
            "Progression allure / 100m",
            fontSize   = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color      = Color(0xFFE0E0E0)
        )
        Text(
            "🟢 sous la moyenne  🔴 au-dessus  ·  plus bas = plus rapide",
            fontSize = 12.sp,
            color    = Color(0xFF757575),
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
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
                    LineChart(ctx).apply {
                        description.isEnabled = false
                        legend.isEnabled      = false
                        setDrawGridBackground(false)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setTouchEnabled(false)
                        extraBottomOffset = 10f

                        xAxis.apply {
                            position            = XAxis.XAxisPosition.BOTTOM
                            textColor           = android.graphics.Color.parseColor("#9E9E9E")
                            textSize            = 11f
                            gridColor           = android.graphics.Color.parseColor("#222222")
                            valueFormatter      = IndexAxisValueFormatter(labels)
                            granularity         = 1f
                            setDrawAxisLine(false)
                            labelRotationAngle  = -30f
                        }
                        axisLeft.apply {
                            textColor   = android.graphics.Color.parseColor("#9E9E9E")
                            textSize    = 11f
                            gridColor   = android.graphics.Color.parseColor("#222222")
                            val minP    = (paceValues.minOrNull() ?: 0f) - 5f
                            axisMinimum = minP.coerceAtLeast(0f)
                            setDrawAxisLine(false)
                            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                                override fun getFormattedValue(v: Float): String {
                                    val m = v.toInt() / 60
                                    val s = v.toInt() % 60
                                    return if (m > 0) "${m}:${"%02d".format(s)}" else "${s}s"
                                }
                            }
                        }
                        axisRight.isEnabled = false

                        val entries    = paceValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }
                        val avgEntries = listOf(
                            Entry(0f, avgPace),
                            Entry((paceValues.size - 1).toFloat(), avgPace)
                        )

                        val mainSet = LineDataSet(entries, "Allure").apply {
                            color          = android.graphics.Color.parseColor("#4FC3F7")
                            lineWidth      = 2.5f
                            setDrawCircles(true)
                            circleRadius   = 5f
                            circleColors   = paceValues.map { v ->
                                if (v <= avgPace) android.graphics.Color.parseColor("#66BB6A")
                                else             android.graphics.Color.parseColor("#EF5350")
                            }
                            setDrawValues(false)
                            mode           = LineDataSet.Mode.CUBIC_BEZIER
                            cubicIntensity = 0.2f
                        }
                        val avgSet = LineDataSet(avgEntries, "Moy").apply {
                            color          = android.graphics.Color.parseColor("#424242")
                            lineWidth      = 1.5f
                            enableDashedLine(10f, 8f, 0f)
                            setDrawCircles(false)
                            setDrawValues(false)
                        }

                        data = LineData(mainSet, avgSet)
                        animateX(600)
                        invalidate()
                    }
                }
            )
        }
    }
}

// ── StatCard ──────────────────────────────────────────────────────────────────

@Composable
fun StatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier            = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 13.sp, color = Color(0xFF757575), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ── SessionCard ───────────────────────────────────────────────────────────────

@Composable
fun SessionCard(session: SessionEntity, onClick: () -> Unit) {
    val date = remember(session.startTimestamp) {
        SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.FRANCE)
            .format(Date(session.startTimestamp))
    }

    val strokeInfo: String? = remember(session.id) {
        val strokes = session.strokeCountsPerLap()
        val laps    = session.lapTimesMs()
        if (strokes.isNotEmpty() && strokes.any { it > 0 }) {
            val avgStr   = strokes.average().toInt()
            val avgSwolf = if (strokes.size == laps.size)
                laps.zip(strokes).map { (ms, s) -> if (s > 0) s + (ms / 1000).toInt() else 0 }
                    .filter { it > 0 }.average().toInt()
            else 0
            if (avgSwolf > 0) "~$avgStr coups · SWOLF $avgSwolf" else "~$avgStr coups/long"
        } else null
    }

    val trend: Pair<String, Color>? = remember(session.id) {
        val laps = session.lapTimesMs()
        if (laps.size >= 4) {
            val mid        = laps.size / 2
            val firstHalf  = laps.take(mid).average()
            val secondHalf = laps.drop(mid).average()
            when {
                secondHalf < firstHalf * 0.97 -> "↗ en forme" to Color(0xFF66BB6A)
                secondHalf > firstHalf * 1.03 -> "↘ fatigue"  to Color(0xFFEF5350)
                else                           -> "→ régulier" to Color(0xFF757575)
            }
        } else null
    }

    val pace = remember(session.id) {
        if (session.poolLengthMeters > 0 && session.averageLapTimeMs > 0)
            session.averageLapTimeMs * 100L / session.poolLengthMeters else 0L
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape  = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Ligne 1 : distance + bassin + tendance
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${session.totalDistanceMeters}m",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFF4FC3F7)
                    )
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        color = Color(0xFF2A2A2A),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "bassin ${session.poolLengthMeters}m",
                            fontSize = 13.sp,
                            color    = Color(0xFF4DB6AC),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                // Indicateur cliquable
                Text("›", fontSize = 30.sp, color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(6.dp))

            // Ligne 2 : longueurs + temps + moyenne
            Text(
                "${session.lapCount} longueurs  ·  ${formatMs(session.totalTimeMs)}  ·  moy ${formatMs(session.averageLapTimeMs)}",
                fontSize = 15.sp,
                color    = Color(0xFFBDBDBD)
            )

            // Ligne 3 : allure/100m
            if (pace > 0L) {
                Text(
                    "Allure : ${formatMs(pace)} / 100m",
                    fontSize = 14.sp,
                    color    = Color(0xFF9575CD),
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            // Ligne 4 : coups + SWOLF
            if (strokeInfo != null) {
                Text(
                    strokeInfo,
                    fontSize = 14.sp,
                    color    = Color(0xFF4DB6AC),
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            // Ligne 5 : tendance + date
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                trend?.let { (label, color) ->
                    Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
                }
                Text(date, fontSize = 13.sp, color = Color(0xFF616161))
            }

            // Hint tap
            Text(
                "Appuie pour le détail →",
                fontSize = 12.sp,
                color    = Color(0xFF37474F),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ── Utilitaires ───────────────────────────────────────────────────────────────

fun formatMs(ms: Long): String {
    if (ms <= 0L) return "—"
    val m = ms / 60000
    val s = (ms % 60000) / 1000
    val t = (ms % 1000) / 100
    return if (m > 0) "%d:%02d.%d".format(m, s, t)
    else "%d.%ds".format(s, t)
}

fun formatTotalTime(ms: Long): String {
    if (ms <= 0L) return "—"
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    val s = (ms % 60_000) / 1_000
    return when {
        h > 0 -> "${h}h %02dm".format(m)
        m > 0 -> "${m}min %02ds".format(s)
        else  -> "${s}s"
    }
}
