package dev.meditimer.app.data

data class Preset(
    val name: String,
    val warmupSec: Int,
    val meditateSec: Int,
    val program: Program? = null // null = simples Preset
)
