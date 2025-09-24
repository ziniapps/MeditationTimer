package dev.meditimer.app.timer

import android.os.CountDownTimer
import dev.meditimer.app.data.Program
import dev.meditimer.app.data.IntervalSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * TimerEngine steuert:
 *  - einfachen Ablauf (Vorlauf -> Meditation)
 *  - Programm-Ablauf (bis zu 5 Intervalle, optional Wiederholungen)
 *
 * Gongs:
 *  - Für den einfachen Ablauf spielt der Host (HomeScreen) die Start/End-Gongs.
 *  - Für Programm-Intervalle ruft die Engine playBuiltin(id) bei Start/Ende.
 */
class TimerEngine(
    private val onPhaseStart: (Phase) -> Unit,
    private val onFinish: () -> Unit,
    /** callback zum Abspielen von Built-in-Gongs (1..3); 0 = kein Gong */
    private val playBuiltin: (Int) -> Unit
) {
    enum class Phase { IDLE, WARMUP, MEDITATE, PROGRAM }

    data class ProgramProgress(
        val currentIndex: Int,         // 1-basiert für Anzeige
        val total: Int,                // Anzahl Intervalle pro Durchlauf
        val completedDurations: List<Int> // Dauer (sec) der bereits abgelaufenen Intervalle (in Reihenfolge 1..k)
    )

    data class Snapshot(
        val phase: Phase,
        val label: String,
        val remainingSec: Int,
        val totalSec: Int,
        val program: ProgramProgress? = null
    )

    // --- Simple ---
    private var warmupLeft = 0
    private var meditateLeft = 0

    // --- Shared ---
    private var total = 0
    private var phase = Phase.IDLE
    private var paused = false
    private var timer: CountDownTimer? = null

    // --- Program ---
    private var program: Program? = null
    private var repIndex = 0                 // wie oft schon fertiggestellt
    private var intervalIndex = 0            // 0-basiert auf program.intervals
    private var intervalLeft = 0
    private var intervalTotal = 0
    private val completedDurations = mutableListOf<Int>()

    private val _state = MutableStateFlow(Snapshot(Phase.IDLE, "", 0, 0, null))
    val state: StateFlow<Snapshot> = _state

    fun start(warmupSec: Int, meditateSec: Int) {
        stop()
        program = null
        warmupLeft = warmupSec.coerceAtLeast(0)
        meditateLeft = meditateSec.coerceAtLeast(1)
        paused = false
        if (warmupLeft > 0) switchToWarmup() else switchToMeditate()
    }

    fun startProgram(pr: Program) {
        stop()
        program = pr
        repIndex = 0
        intervalIndex = 0
        completedDurations.clear()
        paused = false
        if (pr.intervals.isEmpty()) {
            onFinish()
            return
        }
        switchToProgramInterval(startOfLoop = true)
    }

    fun pause() {
        timer?.cancel()
        paused = true
    }

    fun resume() {
        if (!paused) return
        paused = false
        when (phase) {
            Phase.WARMUP   -> scheduleWarmup(warmupLeft)
            Phase.MEDITATE -> scheduleMeditate(meditateLeft)
            Phase.PROGRAM  -> scheduleProgram(intervalLeft)
            else -> {}
        }
    }

    fun stop() {
        timer?.cancel()
        timer = null
        paused = false
        phase = Phase.IDLE
        program = null
        completedDurations.clear()
        _state.value = Snapshot(Phase.IDLE, "", 0, 0, null)
    }

    // ----- Simple flow -----

    private fun switchToWarmup() {
        phase = Phase.WARMUP
        total = warmupLeft
        onPhaseStart(phase)
        _state.value = Snapshot(Phase.WARMUP, "Vorlauf", warmupLeft, total, null)
        scheduleWarmup(warmupLeft)
    }

    private fun scheduleWarmup(seconds: Int) {
        timer?.cancel()
        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(ms: Long) {
                warmupLeft = (ms / 1000).toInt().coerceAtLeast(0)
                _state.value = Snapshot(Phase.WARMUP, "Vorlauf", warmupLeft, total, null)
            }
            override fun onFinish() {
                warmupLeft = 0
                _state.value = Snapshot(Phase.WARMUP, "Vorlauf", 0, total, null)
                switchToMeditate()
            }
        }.start()
    }

    private fun switchToMeditate() {
        phase = Phase.MEDITATE
        total = meditateLeft
        onPhaseStart(phase)
        _state.value = Snapshot(Phase.MEDITATE, "Meditation", meditateLeft, total, null)
        scheduleMeditate(meditateLeft)
    }

    private fun scheduleMeditate(seconds: Int) {
        timer?.cancel()
        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(ms: Long) {
                meditateLeft = (ms / 1000).toInt().coerceAtLeast(0)
                _state.value = Snapshot(Phase.MEDITATE, "Meditation", meditateLeft, total, null)
            }
            override fun onFinish() {
                meditateLeft = 0
                _state.value = Snapshot(Phase.MEDITATE, "Meditation", 0, total, null)
                stop()
                onFinish()
            }
        }.start()
    }

    // ----- Program flow -----

    private fun switchToProgramInterval(startOfLoop: Boolean = false) {
        val pr = program ?: return

        if (intervalIndex >= pr.intervals.size) {
            // Durchlauf fertig
            if (repIndex < pr.repeatCount) {
                repIndex += 1
                intervalIndex = 0
                completedDurations.clear()
            } else {
                stop()
                onFinish()
                return
            }
        }

        val spec: IntervalSpec = pr.intervals[intervalIndex]
        phase = Phase.PROGRAM
        intervalLeft = spec.durationSec.coerceAtLeast(1)
        intervalTotal = intervalLeft

        // Start-Gong dieses Intervalls (nur wenn gesetzt)
        if (spec.startGong.id > 0) {
            playBuiltin(spec.startGong.id)
        }

        onPhaseStart(phase)
        pushProgramState()
        scheduleProgram(intervalLeft)
    }

    private fun scheduleProgram(seconds: Int) {
        val pr = program ?: return
        val spec = pr.intervals[intervalIndex]
        timer?.cancel()
        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(ms: Long) {
                intervalLeft = (ms / 1000).toInt().coerceAtLeast(0)
                pushProgramState()
            }
            override fun onFinish() {
                intervalLeft = 0
                // End-Gong (falls gesetzt)
                if (spec.endGong.id > 0) {
                    playBuiltin(spec.endGong.id)
                }
                // Intervall als abgeschlossen protokollieren (mit seiner Gesamtdauer)
                completedDurations.add(intervalTotal)
                // weiter zum nächsten Intervall
                intervalIndex += 1
                pushProgramState(finished = true) // finaler Snapshot mit 0 Rest
                switchToProgramInterval()
            }
        }.start()
    }

    private fun pushProgramState(finished: Boolean = false) {
        val pr = program ?: return
        val pg = ProgramProgress(
            currentIndex = (if (intervalIndex < pr.intervals.size) intervalIndex + 1 else pr.intervals.size),
            total = pr.intervals.size,
            completedDurations = completedDurations.toList()
        )
        val remain = if (finished) 0 else intervalLeft
        val totalCur = if (intervalIndex < pr.intervals.size) intervalTotal else 0
        _state.value = Snapshot(
            Phase.PROGRAM,
            label = "Intervall ${pg.currentIndex}/${pg.total}",
            remainingSec = remain,
            totalSec = totalCur,
            program = pg
        )
    }
}
