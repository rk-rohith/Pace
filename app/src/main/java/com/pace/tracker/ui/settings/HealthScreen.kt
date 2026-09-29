package com.pace.tracker.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pace.tracker.MainActivity
import com.pace.tracker.PaceApp
import com.pace.tracker.health.HealthConnectAvailability
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.theme.PaceColors
import kotlinx.coroutines.launch

@Composable
fun HealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as PaceApp).container
    val hc = container.healthConnect
    val scope = rememberCoroutineScope()
    val availability = remember { hc.availability() }
    var granted by remember { mutableStateOf(false) }
    var autoSync by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val sensor = container.stepTracker
    var sensorGranted by remember { mutableStateOf(sensor.hasPermission()) }

    LaunchedEffect(Unit) {
        granted = hc.hasPermission()
        autoSync = container.repository.setting(MainActivity.KEY_HC_ENABLED) == "true"
    }

    val hcLauncher = rememberLauncherForActivityResult(hc.permissionContract()) { result ->
        granted = result.containsAll(hc.permissions)
        scope.launch {
            if (granted) {
                container.repository.setSetting(MainActivity.KEY_HC_ENABLED, "true")
                autoSync = true
                status = if (hc.sync()) "Synced the last 7 days of steps." else "Connected. No step data yet."
            } else {
                status = "Permission not granted — manual entry and the in-app counter still work."
            }
        }
    }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        sensorGranted = it
        if (it) {
            sensor.start()
            status = "Step counter enabled while the app is open."
        }
    }

    ScreenScaffold("Health & steps", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            status?.let { Text(it, color = PaceColors.Ahead) }
            SectionCard("Health Connect", icon = Icons.Filled.HealthAndSafety) {
                when (availability) {
                    HealthConnectAvailability.AVAILABLE -> {
                        Text(if (granted) "Connected — reading steps." else "Read your daily steps from Health Connect (Google Fit, Samsung Health, Fitbit…).")
                        if (!granted) {
                            Button(onClick = { hcLauncher.launch(hc.permissions) }, modifier = Modifier.height(48.dp)) { Text("Connect") }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Auto-sync when the app opens", Modifier.weight(1f))
                                Switch(checked = autoSync, onCheckedChange = { on ->
                                    autoSync = on
                                    scope.launch { container.repository.setSetting(MainActivity.KEY_HC_ENABLED, on.toString()) }
                                })
                            }
                            OutlinedButton(onClick = {
                                scope.launch { status = if (hc.sync()) "Synced the last 7 days of steps." else "No step data found." }
                            }, modifier = Modifier.height(48.dp)) { Text("Sync now") }
                        }
                    }
                    HealthConnectAvailability.NEEDS_INSTALL_OR_UPDATE -> {
                        Text("Health Connect isn't installed (or needs an update). On Android 10–13 it's a separate app from the Play Store.")
                        Button(onClick = { runCatching { context.startActivity(hc.installIntent()) } }, modifier = Modifier.height(48.dp)) {
                            Text("Install Health Connect")
                        }
                    }
                    HealthConnectAvailability.UNSUPPORTED -> Text("Health Connect isn't supported on this device. Use manual entry or the in-app counter.")
                }
            }

            SectionCard("In-app step counter", icon = Icons.AutoMirrored.Filled.DirectionsWalk) {
                if (!sensor.isSupported) {
                    Text("This device has no step-counter sensor.")
                } else if (sensorGranted) {
                    Text("Active: steps are counted while Pace is open and added to today's log.")
                } else {
                    Text("Counts steps with the phone's step sensor while the app is open. Needs the Physical activity permission.")
                    Button(onClick = { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }, modifier = Modifier.height(48.dp)) {
                        Text("Allow physical activity")
                    }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }, modifier = Modifier.height(48.dp)) { Text("Open app settings") }
                }
            }

            SectionCard("How steps are combined", icon = Icons.Filled.Info) {
                Text(
                    "Manual entry for a day always wins. Otherwise Pace uses the higher of Health Connect and the " +
                        "in-app counter (they usually overlap, so they're never added together).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SectionCard("Privacy") {
                Text(
                    "Pace is offline-only. Step data read from Health Connect is stored only on this device, " +
                        "used only to show your progress and calories-out estimates, and never shared or uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
