package com.habitsheet.config

/** One third-party component shown on the licences screen. */
data class OpenSourceNotice(val name: String, val license: String, val url: String)

private const val APACHE = "Apache License 2.0"
private const val OFL = "SIL Open Font License 1.1"
private const val ANDROID_SDK = "Android Software Development Kit License"

/**
 * Components shipped in the app. Keep in step with `gradle/libs.versions.toml`, the SPM packages of `iosApp` and the
 * bundled fonts (`OpenSourceNoticesTest` checks the Gradle side). Review the list again before each release.
 */
val openSourceNotices: List<OpenSourceNotice> = listOf(
    OpenSourceNotice("Kotlin and kotlinx (coroutines, datetime, serialization, io)", APACHE, "https://github.com/Kotlin/kotlinx.coroutines"),
    OpenSourceNotice("Compose Multiplatform", APACHE, "https://github.com/JetBrains/compose-multiplatform"),
    OpenSourceNotice("Material 3 for Compose Multiplatform", APACHE, "https://github.com/JetBrains/compose-multiplatform"),
    OpenSourceNotice("AndroidX Lifecycle and Activity", APACHE, "https://developer.android.com/jetpack/androidx"),
    OpenSourceNotice("SQLDelight", APACHE, "https://github.com/sqldelight/sqldelight"),
    OpenSourceNotice("Ktor client", APACHE, "https://github.com/ktorio/ktor"),
    OpenSourceNotice("Google Play services Auth (Android)", ANDROID_SDK, "https://developers.google.com/android/guides/overview"),
    OpenSourceNotice("Google Sign-In for iOS", APACHE, "https://github.com/google/GoogleSignIn-iOS"),
    OpenSourceNotice("Hanken Grotesk (font)", OFL, "https://github.com/marcologous/hanken-grotesk"),
    OpenSourceNotice("Inter (font)", OFL, "https://github.com/rsms/inter"),
    OpenSourceNotice("JetBrains Mono (font)", OFL, "https://github.com/JetBrains/JetBrainsMono"),
)
