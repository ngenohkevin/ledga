package com.ledga.app.ui.design.format

import java.text.Normalizer
import java.util.Locale

/** R98: text as category and icon search compare it — lower case, accents dropped, runs of spaces as one, trimmed. */
object TextFold {
    fun of(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").replace(SPACES, " ").trim().lowercase(Locale.ROOT)

    private val MARKS = Regex("\\p{M}+")
    private val SPACES = Regex("\\s+")
}
