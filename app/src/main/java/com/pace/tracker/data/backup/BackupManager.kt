package com.pace.tracker.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pace.tracker.BuildConfig
import com.pace.tracker.PaceApp
import com.pace.tracker.data.db.PaceDatabase
import com.pace.tracker.data.db.SettingEntity
import com.pace.tracker.photo.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full backup = one .zip with backup.json (every table) + photos/ (every photo file).
 * Restoring replaces everything in the app, so a fresh install gets the whole programme back.
 */
class BackupManager(
    private val context: Context,
    private val db: PaceDatabase,
    private val photos: PhotoStorage,
) {
    private val dao get() = db.backupDao()

    suspend fun snapshot(): BackupSnapshot = BackupSnapshot(
        profiles = dao.profiles(), logs = dao.logs(), meals = dao.meals(), workouts = dao.workouts(),
        photos = dao.photos(), measurements = dao.measurements(), recals = dao.recals(),
        reflections = dao.reflections(), reminders = dao.reminders(),
        settings = dao.settings().filterNot { isDeviceSetting(it.key) }, foods = dao.foods(),
    )

    /** Writes a complete backup to [out]. Returns a one-line summary. */
    suspend fun export(out: OutputStream): String = withContext(Dispatchers.IO) {
        val snap = snapshot()
        val json = BackupCodec.encode(snap, System.currentTimeMillis(), BuildConfig.VERSION_NAME)
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            photos.dir.listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry(PHOTO_PREFIX + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        snap.summary
    }

    /** Replaces all app data with the backup in [input]. Throws [BackupFormatException] for a wrong file. */
    suspend fun restore(input: InputStream): String = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var json: String? = null
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit
                        name == JSON_ENTRY -> json = zip.readBytes().toString(Charsets.UTF_8)
                        name.startsWith(PHOTO_PREFIX) -> {
                            val fileName = File(name).name // strips any path; blocks "../" tricks
                            if (fileName.isNotBlank()) File(staging, fileName).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val text = json ?: throw BackupFormatException("This file isn't a Pace backup (backup.json is missing).")
            val snap = BackupCodec.decode(JSONObject(text)) { File(photos.dir, it).absolutePath }
            if (snap.profiles.isEmpty()) throw BackupFormatException("This backup has no profile in it.")

            db.withTransaction {
                dao.clearMeals(); dao.clearWorkouts(); dao.clearPhotos(); dao.clearMeasurements(); dao.clearRecals()
                dao.clearReflections(); dao.clearReminders(); dao.clearFoods(); dao.clearLogs(); dao.clearProfiles()
                val keep = dao.settings().filter { isDeviceSetting(it.key) }
                dao.clearSettings()
                dao.insertProfiles(snap.profiles); dao.insertLogs(snap.logs); dao.insertMeals(snap.meals)
                dao.insertWorkouts(snap.workouts); dao.insertPhotos(snap.photos); dao.insertMeasurements(snap.measurements)
                dao.insertRecals(snap.recals); dao.insertReflections(snap.reflections); dao.insertReminders(snap.reminders)
                dao.insertFoods(snap.foods)
                dao.insertSettings(snap.settings.filterNot { isDeviceSetting(it.key) } + keep)
            }
            // Swap in the photos only after the database restore succeeded.
            photos.dir.listFiles()?.forEach { it.delete() }
            staging.listFiles()?.forEach { f -> f.copyTo(File(photos.dir, f.name), overwrite = true) }
            snap.summary
        } finally {
            staging.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------- automatic backups to a folder

    suspend fun folderUri(): Uri? = dao.settings().firstOrNull { it.key == KEY_FOLDER }?.value?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    suspend fun setFolder(uri: Uri?) {
        db.settingsDao().upsertSetting(SettingEntity(KEY_FOLDER, uri?.toString() ?: ""))
        if (uri == null) WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) else schedule(context)
    }

    /** Writes a dated backup into the chosen folder and keeps the newest [KEEP] backups there. */
    suspend fun backupToFolder(): String = withContext(Dispatchers.IO) {
        val tree = folderUri() ?: throw IllegalStateException("No backup folder chosen")
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId)
        val name = "pace-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".zip"
        val doc = DocumentsContract.createDocument(resolver, parent, "application/zip", name)
            ?: throw IllegalStateException("Couldn't create a file in the backup folder")
        val summary = resolver.openOutputStream(doc)?.use { export(it) }
            ?: throw IllegalStateException("Couldn't write to the backup folder")
        prune(tree, treeDocId)
        db.settingsDao().upsertSetting(SettingEntity(KEY_LAST, System.currentTimeMillis().toString()))
        summary
    }

    private fun prune(tree: Uri, treeDocId: String) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId)
        val found = mutableListOf<Pair<String, String>>() // name to docId
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val n = c.getString(1) ?: continue
                if (n.startsWith("pace-backup-") && n.endsWith(".zip")) found += n to id
            }
        }
        found.sortedByDescending { it.first }.drop(KEEP).forEach { (_, id) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    companion object {
        const val KEY_FOLDER = "backup_folder_uri"
        const val KEY_LAST = "backup_last_at"
        const val KEY_LAST_ERROR = "backup_last_error"
        private const val JSON_ENTRY = "backup.json"
        private const val PHOTO_PREFIX = "photos/"
        private const val WORK_NAME = "auto_backup"
        const val KEEP = 7

        /** Settings that belong to this phone, not the programme (folder permission, scheduled times). */
        fun isDeviceSetting(key: String) = key.startsWith("backup_") || key.startsWith("next_")

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

/** Daily automatic backup into the user's chosen folder (survives uninstalling the app). */
class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PaceApp).container
        val backup = container.backupManager
        if (backup.folderUri() == null || container.repository.getProfile() == null) return Result.success()
        return try {
            backup.backupToFolder()
            container.repository.setSetting(BackupManager.KEY_LAST_ERROR, "")
            Result.success()
        } catch (e: Exception) {
            container.repository.setSetting(BackupManager.KEY_LAST_ERROR, e.message ?: "Backup failed")
            Result.retry()
        }
    }
}

/** Restore from a user-picked file, then re-arm everything that depends on the data. */
suspend fun com.pace.tracker.AppContainer.restoreBackup(uri: Uri): String {
    val input = withContext(Dispatchers.IO) { appContext.contentResolver.openInputStream(uri) }
        ?: throw BackupFormatException("Couldn't open that file.")
    val summary = input.use { backupManager.restore(it) }
    runCatching { repository.runPendingRecalibrations() }
    runCatching { reminderScheduler.rescheduleAll() }
    runCatching { com.pace.tracker.widget.PaceWidget.refresh(appContext, repository) }
    return summary
}
