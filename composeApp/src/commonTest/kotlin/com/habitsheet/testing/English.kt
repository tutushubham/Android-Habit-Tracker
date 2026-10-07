package com.habitsheet.testing

import com.habitsheet.presentation.UiText

/**
 * Renders a [UiText] to the English copy in `composeResources/values/strings.xml` the way the app would, using the
 * catalog generated from that file ([EnglishCatalog]). Tests that care about the exact wording (sync messages,
 * confirmation dialogs, file errors) use it; tests of behaviour compare [UiText] values instead.
 */
object English {
    fun render(text: UiText): String = when (text) {
        is UiText.Raw -> text.value

        is UiText.Res -> format(EnglishCatalog.strings.getValue(text.resource.key), text.args)

        is UiText.Plural -> format(
            EnglishCatalog.plurals.getValue(text.resource.key).getValue(if (text.quantity == 1) "one" else "other"),
            text.args,
        )

        is UiText.Joined -> text.parts.joinToString(text.separator) { render(it) }
    }

    private fun format(pattern: String, args: List<Any>): String =
        Regex("""%(\d)\$[sd]""").replace(pattern) { match -> args[match.groupValues[1].toInt() - 1].let { if (it is UiText) render(it) else it.toString() } }
}
