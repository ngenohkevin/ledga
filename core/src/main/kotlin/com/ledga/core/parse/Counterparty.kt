package com.ledga.core.parse

/** Who the money went to / came from, as written in the SMS (name normalised). */
data class Counterparty(
    val name: String?,
    val phone: String?,
    /** Paybill "for account" / "Account Number" value. */
    val accountRef: String?,
    /** Agent, till or business number that is not a phone (e.g. "B2C 600100", agent "012345"). */
    val businessNumber: String?,
) {
    /** Groups the same person across full and masked phone forms (People view, PHONE rules). */
    val key: String? get() = CounterpartyKey.of(name, phone)
}

object Names {
    private val WS = Regex("\\s+")
    private val APP_SUFFIX = Regex("""\s*-\s*APP$""", RegexOption.IGNORE_CASE)
    private val TRAILING_PUNCT = Regex("""[\s.,\-]+$""")

    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val s = raw.replace(WS, " ").trim()
            .replace(APP_SUFFIX, "")
            .replace(TRAILING_PUNCT, "")
            .trim()
            .uppercase()
        return s.ifEmpty { null }
    }
}

object Phones {
    /** Full Kenyan mobile number: 07…/01…, 2547…, +2547…. */
    const val FULL = """(?:\+?254|0)[17]\d{8}"""

    /** Masked form M-Pesa prints on receipts, e.g. 0712***111. */
    const val MASKED = """(?:\+?254|0)[17]\d{1,2}\*{2,4}\d{2,3}"""

    /** Canonical local form: digits (and mask `*`) only, `254…`/`+254…` rewritten to `0…`. */
    fun local(phone: String): String {
        val compact = phone.filter { it.isDigit() || it == '*' }
        return if (compact.startsWith("254")) "0" + compact.drop(3) else compact
    }

    /** First 4 + last 3 characters of the local 0-prefixed form. */
    fun key(phone: String): String {
        val local = local(phone)
        return if (local.length < 7) local else local.take(4) + local.takeLast(3)
    }
}

object CounterpartyKey {
    fun of(name: String?, phone: String?): String? {
        if (name == null && phone == null) return null
        if (phone == null) return name
        return "${name.orEmpty()}|${Phones.key(phone)}"
    }
}

private val ACCOUNT_WS = Regex("\\s+")

/** Paybill account text as written, whitespace collapsed; blank → null. */
fun normalizeAccount(raw: String?): String? = raw?.replace(ACCOUNT_WS, " ")?.trim()?.ifEmpty { null }
