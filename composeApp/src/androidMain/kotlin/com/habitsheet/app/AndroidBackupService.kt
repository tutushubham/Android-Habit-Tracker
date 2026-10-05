package com.habitsheet.app

import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.w
import com.habitsheet.ui.BackupResult
import com.habitsheet.ui.BackupService
import java.io.File

/**
 * File-based backup through the system picker.
 *
 * Process death while a picker is open: the text to export is first written to a private cache file, and the
 * picker's result (delivered again by the activity result registry after the Activity is recreated) copies that
 * file to the chosen document. [exportCallback] cannot survive process death, so in that case the outcome is
 * shown as a Toast instead. A pending import is simply dropped then: nothing has been changed, the person
 * taps Import again.
 */
class AndroidBackupService(
    private val activity: ComponentActivity,
    private val logger: Logger = NoOpLogger,
) : BackupService {

    private enum class Kind(val extension: String, val mime: String) {
        Json("json", "application/json"),
        Csv("csv", "text/csv"),
    }

    private var exportCallback: ((BackupResult) -> Unit)? = null
    private var importCallback: ((String) -> Unit)? = null
    private var importFailure: ((String) -> Unit)? = null

    private val createJsonLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument(Kind.Json.mime),
    ) { uri -> finishExport(Kind.Json, uri) }

    private val createCsvLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument(Kind.Csv.mime),
    ) { uri -> finishExport(Kind.Csv, uri) }

    private val openDocumentLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> finishImport(uri) }

    private fun pendingFile(kind: Kind) = File(File(activity.cacheDir, PENDING_DIR), "pending.${kind.extension}")

    override fun exportBackup(json: String, onResult: (BackupResult) -> Unit) =
        startExport(Kind.Json, json, "habitsheet_backup_${System.currentTimeMillis()}.json", onResult)

    override fun exportCsv(csv: String, onResult: (BackupResult) -> Unit) =
        startExport(Kind.Csv, csv, "habitsheet_export_${System.currentTimeMillis()}.csv", onResult)

    private fun startExport(kind: Kind, text: String, fileName: String, onResult: (BackupResult) -> Unit) {
        val file = pendingFile(kind)
        try {
            file.parentFile?.mkdirs()
            BackupStreams.writeBytes({ file.outputStream() }, text.encodeToByteArray())
        } catch (e: Exception) {
            logger.e(TAG, "Preparing the export file failed", e)
            file.delete()
            onResult(BackupResult.Failure("Couldn't prepare the backup. Is the storage full?"))
            return
        }
        exportCallback = onResult
        try {
            when (kind) {
                Kind.Json -> createJsonLauncher.launch(fileName)
                Kind.Csv -> createCsvLauncher.launch(fileName)
            }
        } catch (e: Exception) {
            logger.e(TAG, "Launching the file picker failed", e)
            file.delete()
            exportCallback = null
            onResult(BackupResult.Failure("No app on this device can save files here."))
        }
    }

    private fun finishExport(kind: Kind, uri: Uri?) {
        val file = pendingFile(kind)
        val callback = exportCallback.also { exportCallback = null }
        val result = when {
            uri == null -> BackupResult.Cancelled

            else -> try {
                val bytes = file.takeIf { it.exists() }?.readBytes()
                    ?: throw BackupIoException("The backup to save was lost. Please export again.")
                BackupStreams.writeBytes({ activity.contentResolver.openOutputStream(uri, "wt") }, bytes)
                BackupResult.Success(if (kind == Kind.Json) "Backup saved." else "CSV saved.")
            } catch (e: BackupIoException) {
                logger.e(TAG, "Writing the export failed", e.cause ?: e)
                // A half-written document is worse than none.
                runCatching { DocumentsContract.deleteDocument(activity.contentResolver, uri) }
                BackupResult.Failure(e.userMessage)
            } catch (e: Exception) {
                logger.e(TAG, "Writing the export failed", e)
                runCatching { DocumentsContract.deleteDocument(activity.contentResolver, uri) }
                BackupResult.Failure("Couldn't save the file.")
            }
        }
        file.delete()
        if (callback != null) {
            callback(result)
        } else if (result !is BackupResult.Cancelled) {
            // The screen that asked is gone (process death while the picker was open): tell the person directly.
            logger.w(TAG, "Export finished after the app was recreated")
            val text = when (result) {
                is BackupResult.Success -> result.message
                is BackupResult.Failure -> result.message
                BackupResult.Cancelled -> ""
            }
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
        }
    }

    override fun importBackup(onImport: (String) -> Unit, onFailure: (String) -> Unit) {
        importCallback = onImport
        importFailure = onFailure
        try {
            openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
        } catch (e: Exception) {
            logger.e(TAG, "Launching the file picker failed", e)
            importCallback = null
            importFailure = null
            onFailure("No app on this device can open files here.")
        }
    }

    private fun finishImport(uri: Uri?) {
        val onImport = importCallback.also { importCallback = null }
        val onFailure = importFailure.also { importFailure = null }
        if (uri == null) return // cancelled
        if (onImport == null) {
            logger.w(TAG, "Import file chosen after the app was recreated; ignored")
            return
        }
        val text = try {
            BackupStreams.readText({ activity.contentResolver.openInputStream(uri) })
        } catch (e: BackupIoException) {
            logger.e(TAG, "Reading the import failed", e.cause ?: e)
            onFailure?.invoke(e.userMessage)
            return
        }
        onImport(text)
    }

    private companion object {
        const val TAG = "Backup"
        const val PENDING_DIR = "pending-export"
    }
}
