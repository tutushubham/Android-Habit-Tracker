package com.habitsheet.app

import com.habitsheet.ui.BackupService
import platform.UIKit.UIPasteboard

/** Clipboard transfer keeps backup available until the user saves it elsewhere. */
class IosBackupService : BackupService {
    override val usesClipboard: Boolean = true

    override fun exportBackup(json: String) {
        UIPasteboard.generalPasteboard.string = json
    }

    override fun exportCsv(csv: String) {
        UIPasteboard.generalPasteboard.string = csv
    }

    override fun importBackup(onImport: (String) -> Unit) {
        onImport(UIPasteboard.generalPasteboard.string.orEmpty())
    }
}
