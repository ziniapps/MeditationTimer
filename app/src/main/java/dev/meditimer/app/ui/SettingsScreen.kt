package dev.meditimer.app.ui

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.meditimer.app.data.Settings as AppSettings
import dev.meditimer.app.util.Sound

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    value: AppSettings,
    onChange: (AppSettings) -> Unit,  // -> wird ab jetzt bei JEDEM Change sofort aufgerufen
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val hasDndAccess = nm?.isNotificationPolicyAccessGranted == true
    val scroll = rememberScrollState()

    // System-Dateiauswahl: Start-Gong
    val pickStartLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
            onChange(value.copy(startUri = uri.toString(), useCustomStart = true))
        }
    }
    // System-Dateiauswahl: End-Gong
    val pickEndLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
            onChange(value.copy(endUri = uri.toString(), useCustomEnd = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weitere Einstellungen") },
                actions = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Schließen") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- Start-Gong ---
            Text("Start-Gong", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = value.useCustomStart,
                    onCheckedChange = { on -> onChange(value.copy(useCustomStart = on)) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Eigenes Start-Audio verwenden")
            }

            if (value.useCustomStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { pickStartLauncher.launch(arrayOf("audio/*")) }) { Text("Datei wählen") }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        enabled = value.startUri != null,
                        onClick = { value.startUri?.let { Sound.playUri(ctx, it) } }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start-Gong testen")
                    }
                }
                Text(value.startUri ?: "Keine Datei gewählt", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(1,2,3).forEach { n ->
                        OutlinedButton(
                            onClick = { onChange(value.copy(startGong = n, useCustomStart = false)) },
                            modifier = Modifier.padding(end = 6.dp)
                        ) { Text("gong$n") }
                    }
                    IconButton(onClick = { Sound.playBuiltin(ctx, value.startGong) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start-Gong testen")
                    }
                }
                Text("Aktuell: gong${value.startGong}", style = MaterialTheme.typography.bodySmall)
            }

            Divider()

            // --- End-Gong ---
            Text("End-Gong", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = value.useCustomEnd,
                    onCheckedChange = { on -> onChange(value.copy(useCustomEnd = on)) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Eigenes End-Audio verwenden")
            }

            if (value.useCustomEnd) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { pickEndLauncher.launch(arrayOf("audio/*")) }) { Text("Datei wählen") }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        enabled = value.endUri != null,
                        onClick = { value.endUri?.let { Sound.playUri(ctx, it) } }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "End-Gong testen")
                    }
                }
                Text(value.endUri ?: "Keine Datei gewählt", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(1,2,3).forEach { n ->
                        OutlinedButton(
                            onClick = { onChange(value.copy(endGong = n, useCustomEnd = false)) },
                            modifier = Modifier.padding(end = 6.dp)
                        ) { Text("gong$n") }
                    }
                    IconButton(onClick = { Sound.playBuiltin(ctx, value.endGong) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "End-Gong testen")
                    }
                }
                Text("Aktuell: gong${value.endGong}", style = MaterialTheme.typography.bodySmall)
            }

            Divider()

            // --- DND & Basis-Schalter (sofort speichern) ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = hasDndAccess,
                    onCheckedChange = {
                        ctx.startActivity(
                            Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text("Do-Not-Disturb-Ausnahme erteilen (Systemeinstellung)")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = value.fullscreen,
                    onCheckedChange = { on -> onChange(value.copy(fullscreen = on)) }
                )
                Spacer(Modifier.width(8.dp)); Text("Vollbild während des Timers")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = value.keepAwake,
                    onCheckedChange = { on -> onChange(value.copy(keepAwake = on)) }
                )
                Spacer(Modifier.width(8.dp)); Text("Bildschirm wach halten")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = value.forceAlarmStream,
                    onCheckedChange = { on -> onChange(value.copy(forceAlarmStream = on)) }
                )
                Spacer(Modifier.width(8.dp)); Text("Erzwinge hörbaren Gong (Alarm-Stream)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = value.countUp,
                    onCheckedChange = { on -> onChange(value.copy(countUp = on)) }
                )
                Spacer(Modifier.width(8.dp)); Text("Anzeigeart: Stoppuhr (↑) statt Countdown")
            }

            // Kein „Speichern“/„Abbrechen“ mehr – alles ist live.
        }
    }
}
