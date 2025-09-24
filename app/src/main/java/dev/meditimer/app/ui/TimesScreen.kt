@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.meditimer.app.ui

import android.widget.NumberPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import dev.meditimer.app.data.BuiltinGong
import dev.meditimer.app.data.IntervalSpec
import dev.meditimer.app.data.Preset
import dev.meditimer.app.data.Program
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun TimesScreen(
    presets: List<Preset?>,
    onSaveSlot: (slot: Int, name: String, warmupSec: Int, meditateSec: Int, program: Program?) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zeiten & Presets") },
                actions = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Schließen") } }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = pad + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items((0 until 5).toList()) { idx ->
                val existing = presets.getOrNull(idx)

                var name by remember(existing) { mutableStateOf(existing?.name ?: "Timer ${idx + 1}") }
                var warm by remember(existing) { mutableStateOf(existing?.warmupSec ?: 0) }
                var medit by remember(existing) { mutableStateOf(existing?.meditateSec ?: 10 * 60) }
                var program by remember(existing) { mutableStateOf(existing?.program) }

                var showWarmupDialog by remember { mutableStateOf(false) }
                var showMeditDialog by remember { mutableStateOf(false) }
                var showAdvanced by remember(existing) { mutableStateOf(existing?.program != null) }

                fun saveNow() = onSaveSlot(idx, name, warm, medit, if (showAdvanced) program else null)

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Timer ${idx + 1}", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; saveNow() },
                            label = { Text("Name (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Vorlauf", style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = { showWarmupDialog = true }) {
                                    Text(formatMmSsOrSs(warm))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Meditation", style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = { showMeditDialog = true }) {
                                    Text(formatMmSs(medit))
                                }
                            }
                        }

                        Row {
                            Switch(
                                checked = showAdvanced,
                                onCheckedChange = { on ->
                                    showAdvanced = on
                                    if (!on) program = null
                                    saveNow()
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Advanced (Intervall-Programm)")
                        }

                        if (showAdvanced) {
                            AdvancedProgramEditor(
                                value = program ?: Program(
                                    intervals = listOf(IntervalSpec(60, BuiltinGong.NONE, BuiltinGong.NONE)),
                                    repeatCount = 0
                                ),
                                onChange = { pr -> program = pr; saveNow() }
                            )
                        }
                    }
                }

                if (showWarmupDialog) {
                    MinutesSecondsPickerDialog(
                        title = "Vorlauf wählen",
                        initialSec = warm,
                        minMinutes = 0, maxMinutes = 30,
                        secondStep = 5,
                        allowZero = true,
                        onConfirm = { sec -> warm = sec; saveNow(); showWarmupDialog = false },
                        onDismiss = { showWarmupDialog = false }
                    )
                }
                if (showMeditDialog) {
                    MinutesSecondsPickerDialog(
                        title = "Meditationsdauer wählen",
                        initialSec = medit,
                        minMinutes = 1, maxMinutes = 180,
                        secondStep = 5,
                        allowZero = false,
                        onConfirm = { sec -> medit = max(1, sec); saveNow(); showMeditDialog = false },
                        onDismiss = { showMeditDialog = false }
                    )
                }
            }
        }
    }
}

/* ---------- Advanced Editor ---------- */

@Composable
private fun AdvancedProgramEditor(
    value: Program,
    onChange: (Program) -> Unit
) {
    var repeat by remember(value) { mutableStateOf(value.repeatCount.coerceIn(0, 20)) }
    var items by remember(value) {
        mutableStateOf(value.intervals.take(5).ifEmpty { listOf(IntervalSpec(60)) }.toMutableList())
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IntegerPickerField(
                range = 0..20,
                value = repeat,
                label = "Wiederholungen (0 = einmal)",
                widthDp = 180
            ) { v -> repeat = v; onChange(Program(items, repeat)) }

            IntegerPickerField(
                range = 1..5,
                value = items.size,
                label = "Anzahl Intervalle",
                widthDp = 180
            ) { n ->
                val new = items.toMutableList()
                while (new.size < n) new.add(IntervalSpec(60))
                while (new.size > n) new.removeLast()
                items = new
                onChange(Program(items, repeat))
            }
        }

        items.forEachIndexed { idx, spec ->
            IntervalRow(
                index = idx + 1,
                spec = spec,
                onChange = { ns ->
                    val copy = items.toMutableList()
                    copy[idx] = ns
                    items = copy
                    onChange(Program(items, repeat))
                }
            )
            if (idx < items.lastIndex) Divider()
        }
    }
}

@Composable
private fun IntervalRow(
    index: Int,
    spec: IntervalSpec,
    onChange: (IntervalSpec) -> Unit
) {
    var showDurDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Intervall $index", style = MaterialTheme.typography.titleSmall)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Dauer", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { showDurDialog = true }) {
                    Text(formatMmSs(spec.durationSec))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Start-Gong", style = MaterialTheme.typography.bodyMedium)
                GongPicker(
                    value = spec.startGong,
                    onChange = { onChange(spec.copy(startGong = it)) }
                )
            }
            Column(Modifier.weight(1f)) {
                Text("End-Gong", style = MaterialTheme.typography.bodyMedium)
                GongPicker(
                    value = spec.endGong,
                    onChange = { onChange(spec.copy(endGong = it)) }
                )
            }
        }
    }

    if (showDurDialog) {
        MinutesSecondsPickerDialog(
            title = "Intervall-Dauer wählen",
            initialSec = spec.durationSec,
            minMinutes = 0, maxMinutes = 60,
            secondStep = 5,
            allowZero = false,
            onConfirm = { sec -> onChange(spec.copy(durationSec = max(1, sec))); showDurDialog = false },
            onDismiss = { showDurDialog = false }
        )
    }
}

/* ---------- Gong-Auswahl (DropdownMenu, stabil) ---------- */

@Composable
private fun GongPicker(
    value: BuiltinGong,
    onChange: (BuiltinGong) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (value) {
        BuiltinGong.NONE -> "Keiner"
        BuiltinGong.G1 -> "gong1"
        BuiltinGong.G2 -> "gong2"
        BuiltinGong.G3 -> "gong3"
    }
    Box {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Gong") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                BuiltinGong.NONE to "Keiner",
                BuiltinGong.G1 to "gong1",
                BuiltinGong.G2 to "gong2",
                BuiltinGong.G3 to "gong3"
            ).forEach { (g, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onChange(g); expanded = false }
                )
            }
        }
    }
}

/* ---------- Integer-Picker (Dialog) ---------- */

@Composable
private fun IntegerPickerField(
    range: IntProgression,
    value: Int,
    label: String,
    widthDp: Int = 160,
    onChange: (Int) -> Unit
) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = "$value",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier
            .width(widthDp.dp)
            .clickable { show = true }
    )
    if (show) {
        IntegerPickerDialog(
            range = range,
            initial = value,
            onConfirm = { v -> onChange(v) },
            onDismiss = { show = false }
        )
    }
}

@Composable
private fun IntegerPickerDialog(
    range: IntProgression,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val values = remember(range) { range.toList() }
    var idx by remember {
        mutableStateOf(closestIndex(values, initial))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wert wählen") },
        text = {
            AndroidView(factory = { ctx ->
                NumberPicker(ctx).apply {
                    minValue = 0
                    maxValue = values.size - 1
                    displayedValues = values.map { it.toString() }.toTypedArray()
                    value = idx
                    wrapSelectorWheel = false
                    setOnValueChangedListener { _, _, newIdx -> idx = newIdx }
                }
            })
        },
        confirmButton = { TextButton(onClick = { onConfirm(values[idx]); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/* ---------- Minuten:Sekunden-Picker (Dialog) ---------- */

@Composable
private fun MinutesSecondsPickerDialog(
    title: String,
    initialSec: Int,
    minMinutes: Int,
    maxMinutes: Int,
    secondStep: Int = 5,
    allowZero: Boolean = true,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val secOptions = remember(secondStep) { (0..59 step max(1, secondStep)).toList() }
    var minutes by remember { mutableStateOf(initialSec.coerceAtLeast(0) / 60) }
    var seconds by remember { mutableStateOf(initialSec.coerceAtLeast(0) % 60) }

    // clamp & snap
    minutes = minutes.coerceIn(minMinutes, maxMinutes)
    seconds = secOptions.minByOrNull { abs(it - seconds) } ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AndroidView(factory = { ctx ->
                    NumberPicker(ctx).apply {
                        minValue = minMinutes
                        maxValue = maxMinutes
                        value = minutes
                        wrapSelectorWheel = false
                        setOnValueChangedListener { _, _, v -> minutes = v }
                    }
                })
                AndroidView(factory = { ctx ->
                    NumberPicker(ctx).apply {
                        minValue = 0
                        maxValue = secOptions.size - 1
                        displayedValues = secOptions.map { "%02d".format(it) }.toTypedArray()
                        value = secOptions.indexOf(seconds).coerceAtLeast(0)
                        wrapSelectorWheel = false
                        setOnValueChangedListener { _, _, idx -> seconds = secOptions[idx] }
                    }
                })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val total = minutes * 60 + seconds
                if (allowZero || total > 0) onConfirm(total) else onConfirm(1)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/* ---------- Helpers ---------- */

private fun closestIndex(values: List<Int>, target: Int): Int {
    if (values.isEmpty()) return 0
    var bestIdx = 0
    var bestDiff = Int.MAX_VALUE
    values.forEachIndexed { i, v ->
        val d = abs(v - target)
        if (d < bestDiff) { bestDiff = d; bestIdx = i }
    }
    return bestIdx
}

private fun formatMmSs(sec: Int): String {
    val m = sec / 60; val s = sec % 60
    return "%d:%02d min".format(m, s)
}
private fun formatMmSsOrSs(sec: Int): String =
    if (sec >= 60) formatMmSs(sec) else "${sec}s"
