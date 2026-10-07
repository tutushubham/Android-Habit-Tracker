package com.habitsheet.app

import com.habitsheet.presentation.UiText
import com.habitsheet.resources.*
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
        onResult(BackupResult.Success(UiText.of(Res.string.backup_copied)))
    }

    override fun exportCsv(csv: String, onResult: (BackupResult) -> Unit) {
        UIPasteboard.generalPasteboard.string = csv
        onResult(BackupResult.Success(UiText.of(Res.string.csv_copied)))
    }

    override fun importBackup(onImport: (String) -> Unit, onFailure: (UiText) -> Unit) {
        val text = UIPasteboard.generalPasteboard.string
        if (text.isNullOrBlank()) {
            onFailure(UiText.of(Res.string.backup_clipboard_empty))
        } else {
            onImport(text)
        }
    }
}
