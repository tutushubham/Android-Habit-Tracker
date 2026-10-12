package com.habitsheet.config

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The licences screen must mention every runtime library family in the version catalog. */
class OpenSourceNoticesTest {
    private val catalog = listOf(File("../gradle/libs.versions.toml"), File("gradle/libs.versions.toml")).first { it.isFile }

    private val families = mapOf(
        "io.ktor:ktor-client-core" to "Ktor",
        "io.ktor:ktor-client-okhttp" to "Ktor",
        "io.ktor:ktor-client-darwin" to "Ktor",
        "app.cash.sqldelight" to "SQLDelight",
        "org.jetbrains.kotlinx" to "kotlinx",
        "org.jetbrains.compose" to "Compose",
        "org.jetbrains.androidx.lifecycle" to "Lifecycle",
        "androidx.activity" to "Activity",
        "com.google.android.gms" to "Play services",
    )

    @Test
    fun everyRuntimeLibraryFamilyHasANotice() {
        val modules = Regex("""module\s*=\s*"([^"]+)"""").findAll(catalog.readText()).map { it.groupValues[1] }.toList()
        val required = modules
            .filterNot { "test" in it || "mock" in it || "tooling" in it }
            .mapNotNull { module -> families.entries.firstOrNull { module.startsWith(it.key) }?.value }
            .toSet()
        val names = openSourceNotices.joinToString("\n") { it.name }
        val missing = required.filterNot { it in names }
        assertEquals(emptyList(), missing)
    }
}
