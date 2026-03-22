package com.piscine.timer.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.piscine.timer.phone.data.db.SessionEntity
import com.piscine.timer.phone.ui.formatMs
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    sessions: List<SessionEntity>,
    onBack  : () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd/MM/yy", Locale.FRANCE) }

    // Filtre bassin actif
    val availablePools = remember(sessions) {
        sessions.map { it.poolLengthMeters }.distinct().sorted()
    }
    var filterPool by remember { mutableStateOf(availablePools.firstOrNull() ?: 25) }

    val filtered = remember(sessions, filterPool) {
        sessions.filter { it.poolLengthMeters == filterPool }
            .sortedBy { it.startTimestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analyse comparative", color = Color(0xFF4FC3F7)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color(0xFF4FC3F7))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0D0D))
            )
        },
        containerColor = Color(0xFF0D0D0D)
    ) { padding ->

        LazyColumn(
            modifier            = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding      = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {

            // ── Filtre bassin ─────────────────────────────────────────────
            if (availablePools.size > 1) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availablePools.forEach { pool ->
                            FilterChip(
                                selected = filterPool == pool,
                                onClick  = { filterPool = pool },
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

            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Aucune séance en ${filterPool}m",
                            color = Color(0xFF757575), fontSize = 16.sp)
                    }
                }
                return@LazyColumn
            }

            // ── Records personnels ────────────────────────────────────────
            item {
                SectionTitle("🏅 Records personnels — bassin ${filterPool}m")
                PersonalRecordsCard(filtered)
            }

            // ── Graphique progression meilleure longueur ──────────────────
            if (filtered.size >= 2) {
                item {
                    SectionTitle("📈 Progression meilleure longueur")
                    BestLapProgressionChart(filtered, dateFmt)
                }

                item {
                    SectionTitle("📉 Progression allure / 100m")
                    PaceProgressionChart(filtered, dateFmt)
                }
            }

            // ── Tableau comparatif ────────────────────────────────────────
            item {
                SectionTitle("📋 Comparatif séances")
            }
            items(filtered.reversed()) { session ->
                ComparativeRow(session, filtered, dateFmt)
            }

            // ── Régularité par séance ─────────────────────────────────────
            item {
                SectionTitle("🎯 Régularité (écart-type / longueur)")
                ConsistencyCard(filtered, dateFmt)
            }
        }
    }
}

// ── Section title ─────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text       = text,
        fontSize   = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color      = Color(0xFFE0E0E0),
        modifier   = Modifier.padding(top = 4.dp, bottom = 6.dp)
    )
}

// ── Records personnels ────────────────────────────────────────────────────────

@Composable
private fun PersonalRecordsCard(sessions: List<SessionEntity>) {
    val bestLap      = sessions.minOf { it.bestLapTimeMs }
    val bestAvg      = sessions.minOf { it.averageLapTimeMs }
    val bestDist     = sessions.maxOf { it.totalDistanceMeters }
    val bestLapDate  = sessions.first { it.bestLapTimeMs == bestLap }
        .startTimestamp.let { SimpleDateFormat("dd/MM/yy", Locale.FRANCE).format(Date(it)) }
    val bestAvgDate  = sessions.first { it.averageLapTimeMs == bestAvg }
        .startTimestamp.let { SimpleDateFormat("dd/MM/yy", Locale.FRANCE).format(Date(it)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape  = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RecordRow("🥇 Meilleure longueur", formatMs(bestLap),  Color(0xFFFFB74D), bestLapDate)
            HorizontalDivider(color = Color(0xFF2A2A2A))
            RecordRow("⚡ Meilleure moyenne", formatMs(bestAvg),  Color(0xFF4FC3F7), bestAvgDate)
            HorizontalDivider(color = Color(0xFF2A2A2A))
            RecordRow("📏 Plus longue séance", "${bestDist}m",     Color(0xFF66BB6A), "")
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String, color: Color, date: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(label, fontSize = 14.sp, color = Color(0xFF9E9E9E))
            if (date.isNotEmpty())
                Text(date, fontSize = 11.sp, color = Color(0xFF616161))
        }
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ── Graphique meilleure longueur ──────────────────────────────────────────────

@Composable
private fun BestLapProgressionChart(sessions: List<SessionEntity>, fmt: SimpleDateFormat) {
    val labels = remember(sessions) { sessions.map { fmt.format(Date(it.startTimestamp)) } }
    val values = remember(sessions) { sessions.map { it.bestLapTimeMs / 1000f } }
    val avg    = remember(values)   { values.average().toFloat() }

    ChartCard(
        labels   = labels,
        values   = values,
        avgValue = avg,
        lineColor = "#FFB74D",
        subtitle = "Plus bas = plus rapide"
    )
}

// ── Graphique allure / 100m ───────────────────────────────────────────────────

@Composable
private fun PaceProgressionChart(sessions: List<SessionEntity>, fmt: SimpleDateFormat) {
    val labels = remember(sessions) { sessions.map { fmt.format(Date(it.startTimestamp)) } }
    val values = remember(sessions) {
        sessions.map { s -> s.averageLapTimeMs * 100f / s.poolLengthMeters / 1000f }
    }
    val avg = remember(values) { values.average().toFloat() }

    ChartCard(
        labels    = labels,
        values    = values,
        avgValue  = avg,
        lineColor = "#4FC3F7",
        subtitle  = "Plus bas = plus rapide"
    )
}

@Composable
private fun ChartCard(
    labels   : List<String>,
    values   : List<Float>,
    avgValue : Float,
    lineColor: String,
    subtitle : String
) {
    Text(subtitle, fontSize = 12.sp, color = Color(0xFF757575),
        modifier = Modifier.padding(bottom = 4.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape  = RoundedCornerShape(12.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(190.dp).padding(8.dp),
            factory  = { ctx ->
                LineChart(ctx).apply {
                    description.isEnabled = false
                    legend.isEnabled      = false
                    setDrawGridBackground(false)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setTouchEnabled(false)
                    extraBottomOffset = 10f

                    xAxis.apply {
                        position           = XAxis.XAxisPosition.BOTTOM
                        textColor          = android.graphics.Color.parseColor("#9E9E9E")
                        textSize           = 11f
                        gridColor          = android.graphics.Color.parseColor("#222222")
                        valueFormatter     = IndexAxisValueFormatter(labels)
                        granularity        = 1f
                        setDrawAxisLine(false)
                        labelRotationAngle = -30f
                    }
                    axisLeft.apply {
                        textColor   = android.graphics.Color.parseColor("#9E9E9E")
                        textSize    = 11f
                        gridColor   = android.graphics.Color.parseColor("#222222")
                        axisMinimum = ((values.minOrNull() ?: 0f) - 3f).coerceAtLeast(0f)
                        setDrawAxisLine(false)
                        valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                            override fun getFormattedValue(v: Float): String {
                                val m = v.toInt() / 60; val s = v.toInt() % 60
                                return if (m > 0) "${m}:${"%02d".format(s)}" else "${s}s"
                            }
                        }
                    }
                    axisRight.isEnabled = false

                    val entries    = values.mapIndexed { i, v -> Entry(i.toFloat(), v) }
                    val avgEntries = listOf(Entry(0f, avgValue), Entry((values.size-1).toFloat(), avgValue))

                    val mainSet = LineDataSet(entries, "").apply {
                        color          = android.graphics.Color.parseColor(lineColor)
                        lineWidth      = 2.5f
                        setDrawCircles(true)
                        circleRadius   = 5f
                        circleColors   = values.map { v ->
                            if (v <= avgValue) android.graphics.Color.parseColor("#66BB6A")
                            else              android.graphics.Color.parseColor("#EF5350")
                        }
                        setDrawValues(true)
                        valueTextColor = android.graphics.Color.parseColor("#9E9E9E")
                        valueTextSize  = 9f
                        valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                            override fun getFormattedValue(v: Float): String {
                                val m = v.toInt() / 60; val s = v.toInt() % 60
                                return if (m > 0) "${m}:${"%02d".format(s)}" else "${s}s"
                            }
                        }
                        mode           = LineDataSet.Mode.CUBIC_BEZIER
                        cubicIntensity = 0.2f
                    }
                    val avgSet = LineDataSet(avgEntries, "").apply {
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

// ── Tableau comparatif ────────────────────────────────────────────────────────

@Composable
private fun ComparativeRow(
    session : SessionEntity,
    all     : List<SessionEntity>,
    fmt     : SimpleDateFormat
) {
    val date      = fmt.format(Date(session.startTimestamp))
    val bestAvg   = all.minOf { it.averageLapTimeMs }
    val isPersonalBest = session.bestLapTimeMs == all.minOf { it.bestLapTimeMs }
    val pace      = if (session.poolLengthMeters > 0)
        session.averageLapTimeMs * 100L / session.poolLengthMeters else 0L

    // Delta moy vs record perso
    val delta     = session.averageLapTimeMs - bestAvg
    val deltaStr  = when {
        delta == 0L       -> "🏅 record"
        delta < 5_000L    -> "+${formatMs(delta)}"
        else              -> "+${formatMs(delta)}"
    }
    val deltaColor = if (delta == 0L) Color(0xFFFFB74D) else Color(0xFF757575)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPersonalBest) Color(0xFF1A1500) else Color(0xFF161616)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(date, fontSize = 14.sp, color = Color(0xFF9E9E9E), fontWeight = FontWeight.Medium)
                Text(
                    "${session.lapCount} long  ·  ${session.totalDistanceMeters}m",
                    fontSize = 13.sp, color = Color(0xFF757575)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMs(session.averageLapTimeMs),
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF4FC3F7)
                )
                Text(deltaStr, fontSize = 12.sp, color = deltaColor)
                if (pace > 0L)
                    Text(
                        "${formatMs(pace)}/100m",
                        fontSize = 12.sp, color = Color(0xFF9575CD)
                    )
            }
        }
    }
}

// ── Régularité ────────────────────────────────────────────────────────────────

@Composable
private fun ConsistencyCard(sessions: List<SessionEntity>, fmt: SimpleDateFormat) {
    data class ConsistencyData(val date: String, val stdDev: Double, val lapCount: Int)

    val data = remember(sessions) {
        sessions.mapNotNull { s ->
            val laps = s.lapTimesMs()
            if (laps.size < 2) return@mapNotNull null
            val mean   = laps.average()
            val stdDev = sqrt(laps.sumOf { (it - mean) * (it - mean) } / laps.size) / 1000.0
            ConsistencyData(
                date     = fmt.format(Date(s.startTimestamp)),
                stdDev   = stdDev,
                lapCount = laps.size
            )
        }.sortedBy { it.stdDev }
    }

    if (data.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape  = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // En-tête
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Date",      fontSize = 12.sp, color = Color(0xFF424242), modifier = Modifier.weight(1f))
                Text("Long.",     fontSize = 12.sp, color = Color(0xFF424242), modifier = Modifier.width(48.dp))
                Text("Écart-type", fontSize = 12.sp, color = Color(0xFF424242), modifier = Modifier.width(80.dp))
                Text("",          fontSize = 12.sp, color = Color(0xFF424242), modifier = Modifier.width(60.dp))
            }
            HorizontalDivider(color = Color(0xFF2A2A2A))

            data.forEachIndexed { index, item ->
                val isRegular = item.stdDev < 2.0
                val color     = when {
                    index == 0             -> Color(0xFF66BB6A)   // meilleure régularité
                    item.stdDev > 5.0      -> Color(0xFFEF5350)   // irrégulier
                    else                   -> Color(0xFFBDBDBD)
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(item.date,  fontSize = 14.sp, color = Color(0xFFBDBDBD), modifier = Modifier.weight(1f))
                    Text("${item.lapCount}", fontSize = 14.sp, color = Color(0xFF757575), modifier = Modifier.width(48.dp))
                    Text(
                        "±${"%.1f".format(item.stdDev)}s",
                        fontSize   = 14.sp,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                        color      = color,
                        modifier   = Modifier.width(80.dp)
                    )
                    Text(
                        when {
                            index == 0    -> "🎯 Top"
                            isRegular     -> "✓ Régulier"
                            item.stdDev > 5.0 -> "⚠️ Irrégulier"
                            else          -> ""
                        },
                        fontSize = 12.sp,
                        color    = color,
                        modifier = Modifier.width(80.dp)
                    )
                }
            }
        }
    }
}
