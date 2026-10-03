package com.habitsheet.app

import com.habitsheet.ui.BackupResult
import com.habitsheet.ui.BackupService
import platform.UIKit.UIPasteboard

/**
 * Clipboard transfer keeps backup available until the user saves it elsewhere (a file-based flow is P1-3).
 *
 * Data safety: the exported text is the same version 4 container as on Android (built by `BackupViewModel`).
 * Import only hands the clipboard text to `BackupViewModel`, which parses, checksums and validates it completely
 * before the repository replaces anything; an empty or non-text clipboard is refused here with a clear message.
 */
class IosBackupService : BackupService {
    override val usesClipboard: Boolean = true

    override fun exportBackup(json: String, onResult: (BackupResult) -> Unit) {
        UIPasteboard.generalPasteboard.string = json
        onResult(BackupResult.Success("Backup copied. Paste and save it somewhere safe before copying anything else."))
    }

    override fun exportCsv(csv: String, onResult: (BackupResult) -> Unit) {
        UIPasteboard.generalPasteboard.string = csv
        onResult(BackupResult.Success("CSV copied to clipboard."))
    }

    override fun importBackup(onImport: (String) -> Unit, onFailure: (String) -> Unit) {
        val text = UIPasteboard.generalPasteboard.string
        if (text.isNullOrBlank()) {
            onFailure("The clipboard has no backup text. Copy your backup first, then try again.")
        } else {
            onImport(text)
        }
    }
}
