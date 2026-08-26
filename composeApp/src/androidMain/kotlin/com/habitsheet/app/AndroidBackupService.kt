package com.habitsheet.app

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.habitsheet.ui.BackupService
import java.io.BufferedReader
import java.io.InputStreamReader

class AndroidBackupService(private val activity: ComponentActivity) : BackupService {

    private var pendingDataToExport: String? = null
    private var onImportCallback: ((String) -> Unit)? = null

    private val createJsonLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            activity.contentResolver.openOutputStream(it)?.use { output ->
                output.write(pendingDataToExport?.toByteArray() ?: ByteArray(0))
            }
            pendingDataToExport = null
        }
    }

    private val createCsvLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            activity.contentResolver.openOutputStream(it)?.use { output ->
                output.write(pendingDataToExport?.toByteArray() ?: ByteArray(0))
            }
            pendingDataToExport = null
        }
    }

    private val openDocumentLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val stringBuilder = StringBuilder()
            activity.contentResolver.openInputStream(it)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }
                }
            }
            onImportCallback?.invoke(stringBuilder.toString())
            onImportCallback = null
        }
    }

    override fun exportBackup(json: String) {
        pendingDataToExport = json
        createJsonLauncher.launch("habitsheet_backup_${System.currentTimeMillis()}.json")
    }

    override fun exportCsv(csv: String) {
        pendingDataToExport = csv
        createCsvLauncher.launch("habitsheet_export_${System.currentTimeMillis()}.csv")
    }

    override fun importBackup(onImport: (String) -> Unit) {
        onImportCallback = onImport
        openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream"))
    }
}
