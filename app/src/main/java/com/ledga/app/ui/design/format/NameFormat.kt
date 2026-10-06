package com.ledga.app.ui.design.format

/**
 * Display names (R44, the approved mockups): M-Pesa writes names in capitals ("KPLC PREPAID"); Ledga shows
 * "KPLC Prepaid". A word with no vowel or with a digit stays as written (KPLC, LTD, KCB, 0712***678), as does a single
 * letter and a short list of known acronyms; every other word is title-cased, on both sides of a hyphen (M-Pesa).
 * Stored names never change: rules and `counterpartyKey` read them.
 */
object NameFormat {
    private const val VOWELS = "AEIOUY"
    private val ACRONYMS = setOf("NCBA", "ABSA", "GOTV", "KRA", "NHIF", "SHA", "SHIF", "ICEA", "CIC", "APA", "UAE", "USA", "EABL", "AAR")

    fun display(raw: String): String =
        raw.trim().split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word -> word.split('-').joinToString("-") { part(it) } }

    private fun part(p: String): String = when {
        p.length <= 1 -> p
        p.any { it.isDigit() } || p.none { it.uppercaseChar() in VOWELS } || p.uppercase() in ACRONYMS -> p
        else -> p.lowercase().replaceFirstChar { it.titlecase() }
    }
}
