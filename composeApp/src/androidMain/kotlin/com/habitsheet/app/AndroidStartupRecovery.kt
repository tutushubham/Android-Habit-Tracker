package com.habitsheet.app

import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.i
import com.habitsheet.presentation.UiText
import com.habitsheet.presentation.resolve
import com.habitsheet.resources.*
import com.habitsheet.ui.BackupResult
import com.habitsheet.ui.StartupRecovery
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Recovery actions for the startup failure screen. Create it in `onCreate` (it registers an activity-result
 * launcher). Like [AndroidBackupService], the zip is built in a private cache file before the picker opens so the
 * result survives process death (a Toast then reports the outcome).
 */
internal class AndroidStartupRecovery(
    private val activity: ComponentActivity,
    private val logger: Logger = NoOpLogger,
    private val now: () -> Long = System::currentTimeMillis,
) : StartupRecovery {
    private val files = DatabaseFiles(activity.getDatabasePath(AndroidDriverFactory.DATABASE_NAME).parentFile!!, AndroidDriverFactory.DATABASE_NAME)
    private var exportCallback: ((BackupResult) -> Unit)? = null

    private val createZipLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> finishExport(uri) }

    private fun pendingFile() = File(File(activity.cacheDir, "pending-export"), "startup-database.zip")

    override fun exportRawDatabase(onResult: (BackupResult) -> Unit) {
        if (files.existing().isEmpty()) {
            onResult(BackupResult.Failure(UiText.of(Res.string.recovery_no_file_to_save)))
            return
        }
        val pending = pendingFile()
        try {
            pending.parentFile?.mkdirs()
            BackupStreams.writeBytes({ pending.outputStream() }, java.io.ByteArrayOutputStream().also { files.writeZip(it) }.toByteArray())
        } catch (e: Exception) {
            logger.e(TAG, "Preparing the data file copy failed", e)
            pending.delete()
            onResult(BackupResult.Failure(UiText.of(Res.string.recovery_err_prepare)))
            return
        }
        exportCallback = onResult
        try {
            createZipLauncher.launch("habitsheet_data_files_${now()}.zip")
        } catch (e: Exception) {
            logger.e(TAG, "Launching the file picker failed", e)
            pending.delete()
            exportCallback = null
            onResult(BackupResult.Failure(UiText.of(Res.string.file_err_no_save_app)))
        }
    }

    private fun finishExport(uri: Uri?) {
        val pending = pendingFile()
        val callback = exportCallback.also { exportCallback = null }
        val result = if (uri == null) {
            BackupResult.Cancelled
        } else {
            try {
                val bytes = pending.takeIf { it.exists() }?.readBytes() ?: throw BackupIoException(UiText.of(Res.string.recovery_err_copy_lost))
                BackupStreams.writeBytes({ activity.contentResolver.openOutputStream(uri, "wt") }, bytes)
                BackupResult.Success(UiText.of(Res.string.recovery_saved_file))
            } catch (e: BackupIoException) {
                logger.e(TAG, "Writing the data file copy failed", e.cause ?: e)
                runCatching { DocumentsContract.deleteDocument(activity.contentResolver, uri) }
                BackupResult.Failure(e.userMessage)
            } catch (e: Exception) {
                logger.e(TAG, "Writing the data file copy failed", e)
                runCatching { DocumentsContract.deleteDocument(activity.contentResolver, uri) }
                BackupResult.Failure(UiText.of(Res.string.file_err_save))
            }
        }
        pending.delete()
        if (callback != null) {
            callback(result)
        } else if (result is BackupResult.Success) {
            Toast.makeText(activity, runBlocking { result.message.resolve() }, Toast.LENGTH_LONG).show()
        }
    }

    override fun setAsideDatabase(): BackupResult = try {
        val moved = files.setAside(now())
        logger.i(TAG, "Moved $moved database file(s) aside")
        BackupResult.Success(UiText.of(if (moved == 0) Res.string.recovery_nothing_to_move else Res.string.recovery_moved_aside))
    } catch (e: Exception) {
        logger.e(TAG, "Moving the database aside failed", e)
        BackupResult.Failure(UiText.of(Res.string.recovery_err_move))
    }

    private companion object {
        const val TAG = "StartupRecovery"
    }
}
