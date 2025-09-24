package dev.meditimer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.meditimer.app.data.*
import dev.meditimer.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var presetStore: PresetStore
    private lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presetStore = PresetStore(this)
        settingsStore = SettingsStore(this)

        setContent {
            val nav = rememberNavController()

            val presets by presetStore.presetsFlow.collectAsState(initial = listOf(null, null, null, null, null))
            val settings by settingsStore.flow.collectAsState(
                initial = Settings(
                    startGong = 1, endGong = 2,
                    fullscreen = false, keepAwake = false, forceAlarmStream = true, countUp = false,
                    useCustomStart = false, useCustomEnd = false, startUri = null, endUri = null,
                    lastWarmupSec = 60, lastMeditateSec = 25*60
                )
            )

            NavHost(navController = nav, startDestination = "home") {

                composable("home") {
                    HomeScreen(
                        navigateTimes = { nav.navigate("times") },
                        navigateSettings = { nav.navigate("settings") },
                        presets = presets,
                        // nur die letzten einfachen Zeiten persistieren
                        onUpdateLastUsed = { w, m -> lifecycleScope.launch { settingsStore.saveLastUsed(w, m) } },
                        settings = settings
                    )
                }

                composable("times") {
                    TimesScreen(
                        presets = presets,
                        onSaveSlot = { slot, name, warm, medit, program ->
                            lifecycleScope.launch {
                                presetStore.savePreset(slot, Preset(name, warm, medit, program))
                            }
                        },
                        onBack = { nav.popBackStack() }
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        value = settings,
                        onChange = { new -> lifecycleScope.launch { settingsStore.save(new) } },
                        onBack = { nav.popBackStack() }
                    )
                }
            }
        }
    }
}
