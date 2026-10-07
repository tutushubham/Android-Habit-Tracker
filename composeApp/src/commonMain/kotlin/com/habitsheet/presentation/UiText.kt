// The resource functions take their format arguments as a vararg; the arrays here are a handful of items at most.
@file:Suppress("SpreadOperator")

package com.habitsheet.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Text the person will read, described but not yet rendered: a string resource with its arguments, a plural, or text
 * that came from the person's own data. Presentation code and platform services produce these so that no English is
 * hard-coded outside `composeResources/values/strings.xml`, and tests can compare them without a resource environment.
 * An argument may itself be a [UiText]; it is rendered first.
 */
sealed interface UiText {
    /** Text that is data, not copy: a habit name, a sheet's own wording, a technical detail. Never translated. */
    data class Raw(val value: String) : UiText

    data class Res(val resource: StringResource, val args: List<Any> = emptyList()) : UiText

    data class Plural(val resource: PluralStringResource, val quantity: Int, val args: List<Any> = listOf(quantity)) : UiText

    /** Several texts in a row, e.g. a sync summary "Synced 2 sessions · 1 check uploaded". */
    data class Joined(val parts: List<UiText>, val separator: String = " · ") : UiText

    companion object {
        fun of(resource: StringResource, vararg args: Any): UiText = Res(resource, args.toList())
        fun plural(resource: PluralStringResource, quantity: Int, vararg args: Any): UiText =
            Plural(resource, quantity, if (args.isEmpty()) listOf(quantity) else args.toList())
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value

    is UiText.Res -> stringResource(resource, *renderedArgs(args) { it.asString() })

    is UiText.Plural -> pluralStringResource(resource, quantity, *renderedArgs(args) { it.asString() })

    is UiText.Joined -> {
        val rendered = ArrayList<String>(parts.size)
        for (part in parts) rendered.add(part.asString())
        rendered.joinToString(separator)
    }
}

/** For code outside composition (a toast after process death, logs of what was shown). */
@OptIn(ExperimentalResourceApi::class)
suspend fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> value

    is UiText.Res -> getString(resource, *renderedArgs(args) { it.resolve() })

    is UiText.Plural -> getPluralString(resource, quantity, *renderedArgs(args) { it.resolve() })

    is UiText.Joined -> {
        val rendered = ArrayList<String>(parts.size)
        for (part in parts) rendered.add(part.resolve())
        rendered.joinToString(separator)
    }
}

/** Arguments that are themselves [UiText] are rendered first (inline, so [render] may call composable or suspend code). */
private inline fun renderedArgs(args: List<Any>, render: (UiText) -> String): Array<Any> {
    val out = arrayOfNulls<Any>(args.size)
    for (i in args.indices) out[i] = args[i].let { if (it is UiText) render(it) else it }
    @Suppress("UNCHECKED_CAST")
    return out as Array<Any>
}
