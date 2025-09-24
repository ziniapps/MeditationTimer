package dev.meditimer.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.min

private val Context.presetData by preferencesDataStore("meditimer_presets")

class PresetStore(private val context: Context) {

    // Keys pro Slot (0..4)
    private fun kName(i: Int) = stringPreferencesKey("p${i}_name")
    private fun kWarm(i: Int) = intPreferencesKey("p${i}_warm")
    private fun kMedit(i: Int) = intPreferencesKey("p${i}_medit")
    private fun kProg(i: Int) = stringPreferencesKey("p${i}_prog") // custom encoding

    val presetsFlow: Flow<List<Preset?>> = context.presetData.data.map { p ->
        (0 until 5).map { i ->
            val name = p[kName(i)] ?: return@map null
            val warm = p[kWarm(i)] ?: 0
            val medit = p[kMedit(i)] ?: 0
            val programStr = p[kProg(i)]
            val program = programStr?.let { decodeProgram(it) }
            Preset(name, warm, medit, program)
        }
    }

    suspend fun savePreset(slot: Int, preset: Preset) {
        val s = min(max(slot, 0), 4)
        context.presetData.edit { e ->
            e[kName(s)] = preset.name
            e[kWarm(s)] = preset.warmupSec
            e[kMedit(s)] = preset.meditateSec
            if (preset.program != null) e[kProg(s)] = encodeProgram(preset.program) else e.remove(kProg(s))
        }
    }

    // --- very small manual encoding to avoid extra deps ---
    // Format:
    // repeat=R; intervals=dur-start-end|dur-start-end|...
    // where start/end in {0,1,2,3}
    private fun encodeProgram(pr: Program): String {
        val intervals = pr.intervals.joinToString("|") {
            "${it.durationSec}-${it.startGong.id}-${it.endGong.id}"
        }
        return "repeat=${pr.repeatCount};intervals=$intervals"
    }

    private fun decodeProgram(s: String): Program? {
        val parts = s.split(";")
        if (parts.size != 2) return null
        val repeat = parts[0].removePrefix("repeat=").toIntOrNull() ?: 0
        val intsStr = parts[1].removePrefix("intervals=")
        if (intsStr.isBlank()) return null
        val intervals = intsStr.split("|").mapNotNull { triplet ->
            val a = triplet.split("-")
            if (a.size != 3) return@mapNotNull null
            val dur = a[0].toIntOrNull() ?: return@mapNotNull null
            val sg = a[1].toIntOrNull() ?: 0
            val eg = a[2].toIntOrNull() ?: 0
            IntervalSpec(
                durationSec = dur,
                startGong = BuiltinGong.values().firstOrNull { it.id == sg } ?: BuiltinGong.NONE,
                endGong   = BuiltinGong.values().firstOrNull { it.id == eg } ?: BuiltinGong.NONE
            )
        }
        if (intervals.isEmpty()) return null
        return Program(intervals = intervals.take(5), repeatCount = repeat.coerceIn(0, 20))
    }
}
