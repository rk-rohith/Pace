package com.pace.tracker.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pace.tracker.PaceApp
import com.pace.tracker.data.backup.BackupManager
import com.pace.tracker.data.backup.restoreBackup
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.theme.PaceColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as PaceApp).container
    val backup = container.backupManager
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var folder by remember { mutableStateOf<Uri?>(null) }
    var lastAt by remember { mutableStateOf<Long?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        folder = backup.folderUri()
        lastAt = container.repository.setting(BackupManager.KEY_LAST)?.toLongOrNull()
        lastError = container.repository.setting(BackupManager.KEY_LAST_ERROR)?.takeIf { it.isNotBlank() }
    }
    LaunchedEffect(Unit) { reload() }

    fun perform(block: suspend () -> String) {
        busy = true; status = null; error = null
        scope.launch {
            try {
                status = block()
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
                reload()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) perform {
            val summary = context.contentResolver.openOutputStream(uri)?.use { backup.export(it) }
                ?: throw IllegalStateException("Couldn't write the file.")
            "Backup saved: $summary."
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) perform {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            backup.setFolder(uri)
            val summary = backup.backupToFolder()
            "Automatic backups are on. First backup saved: $summary."
        }
    }

    ScreenScaffold("Backup & restore", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            status?.let { Text(it, color = PaceColors.Ahead) }
            error?.let { Text(it, color = PaceColors.Error) }

            SectionCard("Automatic daily backup", icon = Icons.Filled.Folder) {
                Text(
                    "Pick a folder outside the app, such as Downloads/Pace or a Google Drive folder. Pace saves a full backup " +
                        "there every day and keeps the last ${BackupManager.KEEP}. Files in that folder survive uninstalling " +
                        "the app, so you can restore everything on a new install or a new phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val f = folder
                if (f == null) {
                    Button(onClick = { folderLauncher.launch(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Choose backup folder")
                    }
                } else {
                    Text("Folder: ${folderName(f)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        lastAt?.let { "Last backup: " + formatTime(it) } ?: "No backup yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    lastError?.let { Text("Last automatic backup failed: $it", color = PaceColors.Behind) }
                    Button(onClick = { perform { "Backup saved: ${backup.backupToFolder()}." } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Back up now")
                    }
                    OutlinedButton(onClick = { folderLauncher.launch(null) }, enabled = !busy, modifier = Modifier.height(48.dp)) {
                        Text("Change folder")
                    }
                    TextButton(onClick = { perform { backup.setFolder(null); "Automatic backups turned off." } }, enabled = !busy) {
                        Text("Turn off automatic backups", color = PaceColors.Error)
                    }
                }
            }

            SectionCard("Save a backup file", icon = Icons.Filled.CloudUpload) {
                Text(
                    "One .zip with everything: profile, every day's log, meals, workouts, measurements, progress photos, " +
                        "recalibration history, reflections, reminders and saved foods. Save it anywhere or send it to yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { exportLauncher.launch("pace-backup-${LocalDate.now()}.zip") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Save backup file") }
            }

            SectionCard("Restore", icon = Icons.Filled.Restore) {
                Text(
                    "Pick a Pace backup .zip. Restoring replaces everything currently in the app with the backup.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Restore from backup") }
            }

            Text(
                if (busy) "Working…" else "Android's own cloud backup also copies Pace's data when it's enabled in your phone's settings, " +
                    "but it has a size limit; the folder backup above is the reliable way to keep everything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace all data?") },
            text = { Text("Everything currently in Pace will be replaced by the backup. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    perform { "Restored: ${container.restoreBackup(uri)}." }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

private fun folderName(uri: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifBlank { "Selected folder" } }.getOrDefault("Selected folder")

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.getDefault()))
