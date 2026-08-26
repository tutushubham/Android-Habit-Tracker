package com.habitsheet.app

import android.content.Context
import com.habitsheet.presentation.VersionProvider

class AndroidVersionProvider(private val context: Context) : VersionProvider {
    override val versionName: String
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }

    override val versionCode: Long
        get() = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        } catch (e: Exception) {
            1L
        }
}
