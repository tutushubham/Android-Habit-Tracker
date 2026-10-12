@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.habitsheet.app

import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.w
import com.habitsheet.presentation.UiText
import com.habitsheet.resources.*
import com.habitsheet.ui.BackupResult
import com.habitsheet.ui.BackupService
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

/**
 * File-based backup on iOS: export writes a temporary file and hands it to the share sheet ("Save to Files",
 * AirDrop, Mail …); import opens the system document picker. NOT RUN on a device: it compiles on any machine,
 * but the sheet, the picker and the file locations need a Mac and an iPhone/iPad to verify (see PROGRESS.md).
 *
 * Data safety is unchanged from Android: this class only moves text. `BackupViewModel` builds the version 4
 * container and, on import, parses, checksums and validates the whole file before anything is replaced. The picker
 * opens the file *as a copy* (`asCopy`), so no security-scoped access outlives the read, and the copy is deleted
 * right after reading. A file larger than [MAX_IMPORT_BYTES] is refused before it is read.
 */
internal class IosBackupService(
    private val host: () -> UIViewController?,
    private val logger: Logger = NoOpLogger,
) : BackupService {
    private val fileManager = NSFileManager.defaultManager

    // The picker keeps only a weak reference to its delegate.
    private var pickerDelegate: PickerDelegate? = null

    override fun exportBackup(json: String, onResult: (BackupResult) -> Unit) =
        share(json, "habitsheet_backup_${stamp()}.json", Res.string.backup_shared, onResult)

    override fun exportCsv(csv: String, onResult: (BackupResult) -> Unit) =
        share(csv, "habitsheet_export_${stamp()}.csv", Res.string.csv_shared, onResult)

    private fun stamp(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()

    private fun share(text: String, fileName: String, doneMessage: org.jetbrains.compose.resources.StringResource, onResult: (BackupResult) -> Unit) {
        val presenter = host()
        if (presenter == null) {
            onResult(BackupResult.Failure(UiText.of(Res.string.file_err_no_save_app)))
            return
        }
        val directory = NSTemporaryDirectory() + "habitsheet-export"
        val path = "$directory/$fileName"
        fileManager.removeItemAtPath(directory, null)
        val written = fileManager.createDirectoryAtPath(directory, true, null, null) &&
            NSString.create(string = text).writeToFile(path, true, NSUTF8StringEncoding, null)
        if (!written) {
            logger.w(TAG, "Preparing the export file failed")
            fileManager.removeItemAtPath(directory, null)
            onResult(BackupResult.Failure(UiText.of(Res.string.file_err_prepare_backup)))
            return
        }
        val sheet = UIActivityViewController(listOf(NSURL.fileURLWithPath(path)), null)
        sheet.popoverPresentationController?.apply {
            sourceView = presenter.view
            sourceRect = presenter.view.bounds
            permittedArrowDirections = NO_ARROW
        }
        sheet.completionWithItemsHandler = { _, completed, _, error ->
            fileManager.removeItemAtPath(directory, null)
            onResult(
                when {
                    error != null -> {
                        logger.w(TAG, "The share sheet reported an error")
                        BackupResult.Failure(UiText.of(Res.string.file_err_save))
                    }

                    completed -> BackupResult.Success(UiText.of(doneMessage))

                    else -> BackupResult.Cancelled
                },
            )
        }
        presenter.presentViewController(sheet, true, null)
    }

    override fun importBackup(onImport: (String) -> Unit, onFailure: (UiText) -> Unit) {
        val presenter = host()
        if (presenter == null) {
            onFailure(UiText.of(Res.string.file_err_no_open_app))
            return
        }
        val delegate = PickerDelegate(
            onPicked = { url ->
                pickerDelegate = null
                readPicked(url, onImport, onFailure)
            },
            onCancel = { pickerDelegate = null },
        )
        pickerDelegate = delegate
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOfNotNull(UTTypeJSON, UTTypePlainText, UTTypeData),
            asCopy = true,
        )
        picker.delegate = delegate
        presenter.presentViewController(picker, true, null)
    }

    private fun readPicked(url: NSURL, onImport: (String) -> Unit, onFailure: (UiText) -> Unit) {
        val path = url.path
        val size = path?.let { (fileManager.attributesOfItemAtPath(it, null)?.get(NSFileSize) as? Number)?.toLong() }
        val text = if (size != null && size > MAX_IMPORT_BYTES) {
            null
        } else {
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val read = NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, error.ptr)
                if (read == null) logger.w(TAG, "Reading the picked file failed (${error.value?.code})")
                read
            }
        }
        path?.let { fileManager.removeItemAtPath(it, null) }
        when {
            size != null && size > MAX_IMPORT_BYTES -> onFailure(UiText.of(Res.string.file_err_too_large))
            text == null -> onFailure(UiText.of(Res.string.file_err_open))
            else -> onImport(text)
        }
    }

    private class PickerDelegate(
        private val onPicked: (NSURL) -> Unit,
        private val onCancel: () -> Unit,
    ) : NSObject(),
        UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) onCancel() else onPicked(url)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onCancel()
    }

    private companion object {
        const val TAG = "Backup"
        const val MAX_IMPORT_BYTES = 32L * 1024 * 1024
        const val NO_ARROW = 0uL
        const val MILLIS_PER_SECOND = 1000
    }
}
