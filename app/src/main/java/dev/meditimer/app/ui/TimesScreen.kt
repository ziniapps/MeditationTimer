package dev.meditimer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.meditimer.app.data.BuiltinGong
import dev.meditimer.app.data.IntervalSpec
import dev.meditimer.app.data.Preset
import dev.meditimer.app.data.Program

@Composable
fun TimesScreen(
    presets: List<Preset?>,
    onSaveSlot: (slot: Int, name: String, warmupSec: Int, meditateSec: Int, program: Program?) -> Unit,
    onBack: () -> Unit
) {
    val slotCount = maxOf(1, presets.size.takeIf { it > 0 } ?: 6)
    var selectedSlot by remember { mutableStateOf(0) }
    val existing = presets.getOrNull(selectedSlot)

    var name by remember(existing) { mutableStateOf(existing?.name ?: "Preset ${selectedSlot + 1}") }
    var warm by remember(existing) { mutableStateOf(existing?.warmupSec ?: 60) }
    var medit by remember(existing) { mutableStateOf(existing?.meditateSec ?: 600) }
    var showAdvanced by remember { mutableStateOf(false) }

    // Simple mode gongs
    var warmStartGong by remember(existing) { mutableStateOf(BuiltinGong.NONE) }
    var warmEndGong by remember(existing) { mutableStateOf(BuiltinGong.G1) }
    var meditStartGong by remember(existing) { mutableStateOf(BuiltinGong.G1) }
    var meditEndGong by remember(existing) { mutableStateOf(BuiltinGong.G1) }

    // Advanced: config
    var repeats by remember { mutableStateOf(0) }     // 0..10 (0 = einmalig)
    var intervalsCount by remember { mutableStateOf(1) }   // 1..5
    var muteWarmupStart by remember { mutableStateOf(true) }

    data class AdvItem(
        val durationSec: Int,
        val start: BuiltinGong,
        val end: BuiltinGong
    )

    var advItems by remember {
        mutableStateOf(
            MutableList(1) { AdvItem(durationSec = 600, start = BuiltinGong.G1, end = BuiltinGong.G1) }
        )
    }

    // Ensure size 1..5
    fun ensureAdvSize(n: Int) {
        val target = n.coerceIn(1, 5)
        val list = advItems.toMutableList()
        if (target > list.size) {
            repeat(target - list.size) {
                list.add(AdvItem(600, BuiltinGong.G1, BuiltinGong.G1))
            }
        } else if (target < list.size) {
            while (list.size > target) list.removeLast()
        }
        advItems = list
        intervalsCount = target
    }

    fun buildProgram(): Program {
        val intervals = buildList {
            if (warm > 0) add(IntervalSpec(warm, if (muteWarmupStart) BuiltinGong.NONE else warmStartGong, warmEndGong))
            if (showAdvanced) {
                advItems.forEach { add(IntervalSpec(it.durationSec, it.start, it.end)) }
            } else {
                if (medit > 0) add(IntervalSpec(medit, meditStartGong, meditEndGong))
            }
        }
        val repeatCount = if (showAdvanced) repeats.coerceIn(0, 10) else 0
        return Program(intervals = intervals, repeatCount = repeatCount)
    }

    fun saveNow() {
        val program = buildProgram()
        onSaveSlot(selectedSlot, name, warm, if (showAdvanced) 0 else medit, program)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Zeiten & Presets", style = MaterialTheme.typography.titleLarge)
        Divider()

        // Preset-Slots (1..N)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until slotCount) {
                val selected = i == selectedSlot
                OutlinedButton(onClick = { selectedSlot = i }) {
                    Text(if (selected) "[${i+1}]" else "${i+1}")
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )

        // Vorlauf
        SectionCard("Vorlauf") {
            NumberField("Vorlauf (Sekunden)", warm) { warm = it.coerceAtLeast(0) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Kein Gong am Anfang des Vorlaufs")
                Switch(checked = muteWarmupStart, onCheckedChange = { muteWarmupStart = it })
            }
            PickerButtonGong("Gong am Ende des Vorlaufs", warmEndGong) { warmEndGong = it }
        }

        // Simple mode
        if (!showAdvanced) {
            SectionCard("Meditation") {
                NumberField("Meditation (Sekunden)", medit) { medit = it.coerceAtLeast(0) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) { PickerButtonGong("Gong Anfang Meditation", meditStartGong) { meditStartGong = it } }
                    Column(Modifier.weight(1f)) { PickerButtonGong("Gong Ende Meditation", meditEndGong) { meditEndGong = it } }
                }
            }
        }

        // Advanced toggle
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Advanced (Intervall-Programm)", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = showAdvanced, onCheckedChange = { on -> showAdvanced = on })
        }

        // Advanced controls
        if (showAdvanced) {
            SectionCard("Programm") {
                // Wiederholungen
                IntegerPickerButton(
                    label = "Wiederholungen (0..10) — 0 = einmalig",
                    display = repeats.toString(),
                    value = repeats,
                    options = (0..10).toList(),
                    onPick = { repeats = it.coerceIn(0, 10) }
                )
                Spacer(Modifier.height(8.dp))
                // Anzahl Intervalle
                IntegerPickerButton(
                    label = "Anzahl Intervalle (1..5)",
                    display = intervalsCount.toString(),
                    value = intervalsCount,
                    options = (1..5).toList(),
                    onPick = { ensureAdvSize(it) }
                )
            }

            // Intervalldetails
            advItems.forEachIndexed { idx, item ->
                SectionCard("Meditation ${idx + 1}") {
                    NumberField("Dauer (Sekunden)", item.durationSec) { v ->
                        val list = advItems.toMutableList()
                        list[idx] = list[idx].copy(durationSec = v.coerceIn(10, 24*3600))
                        advItems = list
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) { PickerButtonGong("Gong Anfang", item.start) { g ->
                            val list = advItems.toMutableList(); list[idx] = list[idx].copy(start = g); advItems = list
                        }}
                        Column(Modifier.weight(1f)) { PickerButtonGong("Gong Ende", item.end) { g ->
                            val list = advItems.toMutableList(); list[idx] = list[idx].copy(end = g); advItems = list
                        }}
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { saveNow() }) { Text("Speichern") }
            TextButton(onClick = onBack) { Text("Zurück") }
        }
    }
}

// ---------- Reusable UI ----------

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            val filtered = s.filter { it.isDigit() }
            text = filtered
            filtered.toIntOrNull()?.let { onValue(it) }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PickerButtonGong(label: String, value: BuiltinGong, onPick: (BuiltinGong) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(value.name) }
    if (open) {
        GongDialog(
            current = value,
            onSelect = { onPick(it) },
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun IntegerPickerButton(
    label: String,
    display: String,
    value: Int,
    options: List<Int>,
    onPick: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(display) }
    if (open) {
        IntegerPickerDialog(
            title = label,
            value = value,
            options = options,
            onSelect = { onPick(it) },
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun GongDialog(
    current: BuiltinGong,
    onSelect: (BuiltinGong) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gong wählen") },
        text = {
            Column {
                listOf(BuiltinGong.NONE, BuiltinGong.G1, BuiltinGong.G2, BuiltinGong.G3).forEach { g ->
                    val choice = g
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice); onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            when (g) {
                                BuiltinGong.NONE -> "Kein Gong"
                                BuiltinGong.G1 -> "gong1"
                                BuiltinGong.G2 -> "gong2"
                                BuiltinGong.G3 -> "gong3"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (g == current) Text("✓")
                    }
                    Divider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
private fun IntegerPickerDialog(
    title: String,
    value: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { n ->
                    val choice = n // capture value for click lambda
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice); onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(choice.toString(), style = MaterialTheme.typography.bodyLarge)
                    }
                    Divider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}