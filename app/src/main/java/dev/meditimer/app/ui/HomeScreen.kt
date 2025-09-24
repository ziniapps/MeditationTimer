package dev.meditimer.app.ui

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meditimer.app.data.Preset
import dev.meditimer.app.data.Program
import dev.meditimer.app.data.Settings
import dev.meditimer.app.timer.TimerEngine
import dev.meditimer.app.util.KeepScreenOn
import dev.meditimer.app.util.Sound

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navigateTimes: () -> Unit,
    navigateSettings: () -> Unit,
    presets: List<Preset?>,
    onUpdateLastUsed: (warmupSec: Int, meditateSec: Int) -> Unit,
    settings: Settings
) {
    val ctx = LocalContext.current
    val activity = ctx as Activity

    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(TimerEngine.Phase.IDLE) }
    var remaining by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var label by remember { mutableStateOf("") }
    var pgCompleted by remember { mutableStateOf<List<Int>>(emptyList()) }
    var pgCurrentIndex by remember { mutableStateOf(0) }
    var pgTotal by remember { mutableStateOf(0) }

    // zuletzt gewählte einfache Zeiten
    var lastWarmup by remember { mutableStateOf(settings.lastWarmupSec) }
    var lastMedit by remember { mutableStateOf(settings.lastMeditateSec) }
    // aktuell gewähltes Programm (optional)
    var selectedProgram by remember { mutableStateOf<Program?>(null) }

    fun playStartGongSimple() {
        if (settings.useCustomStart && settings.startUri != null) Sound.playUri(ctx, settings.startUri)
        else Sound.playBuiltin(ctx, settings.startGong)
    }
    fun playEndGongSimple() {
        if (settings.useCustomEnd && settings.endUri != null) Sound.playUri(ctx, settings.endUri)
        else Sound.playBuiltin(ctx, settings.endGong)
    }

    // Engine mit Callback für Built-in-Intervall-Gongs
    val engine = remember(settings) {
        TimerEngine(
            onPhaseStart = { newPhase ->
                if (newPhase == TimerEngine.Phase.MEDITATE) playStartGongSimple()
            },
            onFinish = {
                running = false
                paused = false
                phase = TimerEngine.Phase.IDLE
                label = ""
                pgCompleted = emptyList()
                pgCurrentIndex = 0
                pgTotal = 0
                playEndGongSimple()
            },
            playBuiltin = { id -> if (id > 0) Sound.playBuiltin(ctx, id) }
        )
    }

    // State sammeln
    LaunchedEffect(engine) {
        engine.state.collect { snap ->
            phase = snap.phase
            label = snap.label
            remaining = snap.remainingSec
            total = snap.totalSec
            val pg = snap.program
            if (pg != null) {
                pgCompleted = pg.completedDurations
                pgCurrentIndex = pg.currentIndex
                pgTotal = pg.total
            } else {
                pgCompleted = emptyList()
                pgCurrentIndex = 0
                pgTotal = 0
            }
        }
    }

    KeepScreenOn(activity, enabled = settings.keepAwake && running)

    // Anzeige-Werte (Simple)
    val displayWarmup: Int = when (phase) {
        TimerEngine.Phase.WARMUP   -> remaining
        TimerEngine.Phase.MEDITATE -> 0
        else                       -> lastWarmup
    }
    val displayMedit: Int = when (phase) {
        TimerEngine.Phase.WARMUP   -> lastMedit
        TimerEngine.Phase.MEDITATE -> remaining
        else                       -> lastMedit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meditations-Timer", fontSize = 18.sp) },
                actions = {
                    IconButton(onClick = navigateTimes) { Icon(Icons.Default.Tune, contentDescription = "Zeiten & Presets") }
                    IconButton(onClick = navigateSettings) { Icon(Icons.Default.Settings, contentDescription = "Weitere Einstellungen") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            if (phase == TimerEngine.Phase.PROGRAM) {
                // OBEN: abgelaufene Intervalle (kleiner Text), in der Reihenfolge 1..k
                Column(Modifier.fillMaxWidth()) {
                    pgCompleted.forEachIndexed { idx, dur ->
                        Text(
                            text = "Intervall ${idx + 1} – abgelaufen: ${formatHms(dur)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // AKTUELL: groß und zentral
                Text(
                    if (label.isNotBlank()) label else "Intervall ${pgCurrentIndex}/${pgTotal}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    formatHms(remaining),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                // Simple-Modus: zwei feste Zeilen
                Text("Vorlauf", fontSize = 14.sp, color = Color.Gray)
                Text(formatHms(displayWarmup), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Meditation", fontSize = 14.sp, color = Color.Gray)
                Text(formatHms(displayMedit), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(28.dp))

            // Steuerung
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!running) {
                    Button(onClick = {
                        if (selectedProgram != null) {
                            engine.startProgram(selectedProgram!!)
                        } else {
                            engine.start(lastWarmup, lastMedit)
                            onUpdateLastUsed(lastWarmup, lastMedit)
                        }
                        running = true
                        paused = false
                    }) { Text("Start") }
                }
                if (running && !paused) {
                    OutlinedButton(onClick = { engine.pause(); paused = true }) { Text("Pause") }
                }
                OutlinedButton(onClick = {
                    engine.stop()
                    Sound.stop() // Gong sofort stumm
                    running = false
                    paused = false
                    label = ""
                    pgCompleted = emptyList()
                    pgCurrentIndex = 0
                    pgTotal = 0
                }) { Text("Neustart") }
            }

            Spacer(Modifier.height(20.dp))

            Text("Presets", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(5) { idx ->
                    val p = presets.getOrNull(idx)
                    PresetCircle(
                        enabled = p != null && !running,
                        meditateSec = p?.meditateSec ?: 0,
                        warmupSec = p?.warmupSec ?: 0,
                        onClick = {
                            if (p != null) {
                                // Nur Vorauswahl setzen, nicht starten
                                lastWarmup = p.warmupSec
                                lastMedit = p.meditateSec
                                selectedProgram = p.program
                                onUpdateLastUsed(p.warmupSec, p.meditateSec)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetCircle(
    enabled: Boolean,
    meditateSec: Int,
    warmupSec: Int,
    onClick: () -> Unit
) {
    val bg = if (enabled) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFEDEDED)
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!enabled) {
            Text("–", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatMmSs(meditateSec), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, textAlign = TextAlign.Center)
                Text("+ ${formatMmSsOrSs(warmupSec)}", fontSize = 10.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
            }
        }
    }
}

/* ----- Utils ----- */
private fun formatMmSs(sec: Int): String { val m = sec / 60; val s = sec % 60; return "%02d:%02d".format(m, s) }
private fun formatMmSsOrSs(sec: Int): String = if (sec >= 60) formatMmSs(sec) else "${sec}s"
private fun formatHms(sec: Int): String { val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60; return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s) }
