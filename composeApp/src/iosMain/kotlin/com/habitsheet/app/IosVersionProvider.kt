package com.habitsheet.app

import com.habitsheet.presentation.VersionProvider
import platform.Foundation.NSBundle

class IosVersionProvider : VersionProvider {
    override val versionName: String
        get() = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "1.0.0"

    override val versionCode: Long
        get() = (NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String)?.toLong() ?: 1L
}
