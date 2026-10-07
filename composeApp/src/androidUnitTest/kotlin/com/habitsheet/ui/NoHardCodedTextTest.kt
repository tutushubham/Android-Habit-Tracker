package com.habitsheet.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * P1-3 step 1 guard: every word the person reads lives in `composeResources/values/strings.xml`. Screens may not pass a
 * string literal containing letters to `Text`, or to the text-like parameters (`label`, `placeholder`, `title`,
 * `contentDescription`, `onClickLabel`, `stateDescription`, ...). Symbols such as "✓" or "›" and data are fine.
 * Presentation code must not hard-code the copy either: it produces `UiText`.
 */
class NoHardCodedTextTest {
    private val root = listOf(File("src/commonMain/kotlin/com/habitsheet"), File("composeApp/src/commonMain/kotlin/com/habitsheet"))
        .first { it.isDirectory }

    private fun files(dir: String) = File(root, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private val literal = """"(?:[^"\\\n]|\\.)*[A-Za-z](?:[^"\\\n]|\\.)*""""
    private val textLike = Regex(
        """(?:\bText\(\s*|\b(?:text|label|placeholder|title|contentDescription|onClickLabel|stateDescription|supportingText|description|buttonText)\s*=\s*(?:\{\s*Text\(\s*)?)($literal)""",
    )

    /** Letters left in a literal once `${expressions}` and `$names` are removed: only those are copy. */
    private fun hasWords(literal: String): Boolean =
        literal.replace(Regex("""\$\{[^}]*}"""), "").replace(Regex("""\$\w+"""), "").any { it in 'a'..'z' || it in 'A'..'Z' }

    /** Preview-only sample data lives in files named *PreviewData*; it may use English. */
    private fun isPreviewData(f: File) = f.name.contains("PreviewData")

    @Test
    fun screensHaveNoHardCodedCopy() {
        val offenders = files("ui").filterNot(::isPreviewData).flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                val code = line.substringBefore("//")
                if (code.trimStart().startsWith("import ") || code.trimStart().startsWith("*")) return@mapIndexedNotNull null
                textLike.findAll(code)
                    .firstOrNull { hasWords(it.groupValues[1]) }
                    ?.let { "${file.relativeTo(root).invariantSeparatorsPath}:${i + 1}: ${it.groupValues[1]}" }
            }
        }
        assertEquals(emptyList(), offenders, "Move these into composeResources/values/strings.xml")
    }

    @Test
    fun theStringCatalogIsConsistent() {
        val xml = File(root.parentFile.parentFile.parentFile.parentFile, "commonMain/composeResources/values/strings.xml")
            .takeIf { it.isFile } ?: File(root, "../../../../composeResources/values/strings.xml")
        val text = xml.readText()
        val names = Regex("""<(?:string|plurals) name="(\w+)"""").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(names.size, names.toSet().size, "duplicate resource names: ${names.groupingBy { it }.eachCount().filter { it.value > 1 }.keys}")

        // Every resource is used somewhere, and every use refers to a resource.
        val src = root.parentFile.parentFile.parentFile.parentFile // .../composeApp/src
        val code = (
            files("") + File(src, "androidMain").walkTopDown().filter { it.extension == "kt" } +
                File(src, "iosMain").walkTopDown().filter { it.extension == "kt" }
            )
            .joinToString("\n") { it.readText() }
        val used = Regex("""Res\.(?:string|plurals)\.(\w+)""").findAll(code).map { it.groupValues[1] }.toSet()
        assertEquals(emptySet(), used - names.toSet(), "used but not defined")
        assertEquals(emptySet(), names.toSet() - used, "defined but never used")

        // Android-style escapes: a bare apostrophe or an unescaped ampersand breaks the resource compiler.
        val bad = Regex("""<string[^>]*>([^<]*)</string>""").findAll(text).map { it.groupValues[1] }
            .filter { Regex("""(?<!\\)'""").containsMatchIn(it) || Regex("""&(?!amp;|lt;|gt;|quot;)""").containsMatchIn(it) }.toList()
        assertEquals(emptyList(), bad)
    }
}
