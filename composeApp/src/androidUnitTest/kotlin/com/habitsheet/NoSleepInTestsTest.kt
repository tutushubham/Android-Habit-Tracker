package com.habitsheet

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests wait for events (a deferred, a state flow, the virtual clock), never for a fixed time: a fixed sleep is
 * either too short on a slow machine or wasted on a fast one. See docs/TESTING.md, "No sleeping".
 */
class NoSleepInTestsTest {
    private val moduleRoot = listOf(File("src"), File("composeApp/src")).first { it.isDirectory }

    private val forbidden = listOf(
        Regex("""Thread\s*\.\s*sleep\s*\("""),
        Regex("""SystemClock\s*\.\s*sleep\s*\("""),
        Regex("""\.sleep\s*\(\s*\d"""), // TimeUnit.SECONDS.sleep(1)
        Regex("""\bsleep\s*\(\s*\d"""), // sleep(500)
    )

    @Test
    fun noTestSourceSleeps() {
        val testSets = listOf("commonTest", "androidUnitTest", "androidInstrumentedTest", "iosTest")
        val offenders = testSets.map { File(moduleRoot, it) }.filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "NoSleepInTestsTest.kt" }.toList() }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> !line.trimStart().startsWith("//") && forbidden.any { it.containsMatchIn(line) } }
                    .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }

        assertTrue(offenders.isEmpty(), "Wait for an event instead of sleeping:\n" + offenders.joinToString("\n"))
    }
}
