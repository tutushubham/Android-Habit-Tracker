@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.habitsheet.app

import com.habitsheet.data.DatabaseFileNames
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.i
import com.habitsheet.ui.BackupResult
import com.habitsheet.ui.StartupRecovery
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * Recovery actions for the startup failure screen on iOS. NOT RUN: this compiles on any machine but the file
 * locations and the share sheet need a Mac to verify (see PROGRESS.md).
 *
 * The database lives where `NativeSqliteDriver` put it, which differs between library versions, so the directory
 * is found by looking for the file in the usual places instead of assuming one.
 */
internal class IosStartupRecovery(
    private val host: () -> UIViewController?,
    private val logger: Logger = NoOpLogger,
) : StartupRecovery {
    private val fileManager = NSFileManager.defaultManager
    private val name = "habit-sheet.db"

    private fun candidateDirectories(): List<String> = listOf(NSDocumentDirectory, NSApplicationSupportDirectory, NSLibraryDirectory)
        .mapNotNull { NSSearchPathForDirectoriesInDomains(it, NSUserDomainMask, true).firstOrNull() as? String }
        .flatMap { listOf(it, "$it/databases") }

    private fun databaseDirectory(): String? = candidateDirectories().firstOrNull { fileManager.fileExistsAtPath("$it/$name") }

    override fun exportRawDatabase(onResult: (BackupResult) -> Unit) {
        val directory = databaseDirectory()
        val presenter = host()
        if (directory == null || presenter == null) {
            onResult(BackupResult.Failure("There is no data file on this device to save."))
            return
        }
        val staging = NSTemporaryDirectory() + "habitsheet-data-files"
        fileManager.removeItemAtPath(staging, null)
        val copied = try {
            if (!fileManager.createDirectoryAtPath(staging, true, null, null)) throw IllegalStateException("staging")
            DatabaseFileNames.filesOf(name).filter { fileManager.fileExistsAtPath("$directory/$it") }.map { file ->
                if (!fileManager.copyItemAtPath("$directory/$file", "$staging/$file", null)) throw IllegalStateException("copy")
                NSURL.fileURLWithPath("$staging/$file")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Preparing the data file copy failed", e)
            onResult(BackupResult.Failure("Couldn't prepare the copy. Is the storage full?"))
            return
        }
        val sheet = UIActivityViewController(copied, null)
        sheet.popoverPresentationController?.apply {
            sourceView = presenter.view
            sourceRect = presenter.view.bounds
            permittedArrowDirections = 0uL
        }
        sheet.completionWithItemsHandler = { _, completed, _, error ->
            fileManager.removeItemAtPath(staging, null)
            onResult(
                when {
                    error != null -> BackupResult.Failure("Couldn't save the files.")
                    completed -> BackupResult.Success("Saved. Keep these files safe; they contain your habits and history.")
                    else -> BackupResult.Cancelled
                },
            )
        }
        presenter.presentViewController(sheet, true, null)
    }

    override fun setAsideDatabase(): BackupResult {
        val directory = databaseDirectory() ?: return BackupResult.Success("There was no data file to move.")
        val timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
        val moved = mutableListOf<Pair<String, String>>()
        try {
            for (file in DatabaseFileNames.filesOf(name).filter { fileManager.fileExistsAtPath("$directory/$it") }) {
                val from = "$directory/$file"
                val to = "$directory/${DatabaseFileNames.asideName(file, timestamp)}"
                if (!move(from, to)) throw IllegalStateException("move")
                moved += from to to
            }
        } catch (e: Exception) {
            moved.reversed().forEach { (from, to) -> move(to, from) }
            logger.e(TAG, "Moving the database aside failed", e)
            return BackupResult.Failure("Couldn't move the data file. Nothing was changed.")
        }
        val all = fileManager.contentsOfDirectoryAtPath(directory, null).orEmpty().filterIsInstance<String>()
        DatabaseFileNames.staleAsideNames(all, name, keep = 3).forEach { fileManager.removeItemAtPath("$directory/$it", null) }
        logger.i(TAG, "Moved ${moved.size} database file(s) aside")
        return BackupResult.Success("The old data file was moved aside.")
    }

    private fun move(from: String, to: String): Boolean = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        fileManager.moveItemAtPath(from, to, error.ptr)
    }

    private companion object {
        const val TAG = "StartupRecovery"
    }
}
