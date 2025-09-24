package dev.meditimer.app.data

/** Built-in gong IDs (0 = None, 1..3 = builtin) */
enum class BuiltinGong(val id: Int) { NONE(0), G1(1), G2(2), G3(3) }

data class IntervalSpec(
    val durationSec: Int,
    val startGong: BuiltinGong = BuiltinGong.NONE,
    val endGong: BuiltinGong = BuiltinGong.NONE
)

data class Program(
    val intervals: List<IntervalSpec>, // 1..5
    val repeatCount: Int               // 0..20 (0 = einmalig)
)
