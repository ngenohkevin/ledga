package com.ledga.core.parse

import java.security.MessageDigest

object SmsText {
    // Every Unicode White_Space char (no-break, figure, em, thin and ideographic spaces, NEL, line/paragraph
    // separators) collapses to one space; Java's plain \s is ASCII-only.
    private val WHITESPACE = Regex("""\p{IsWhite_Space}+""")
    // Every invisible format char (Unicode category Cf: BOM, zero-width space/joiners, word joiner, LRM/RLM,
    // soft hyphen, bidi embeddings, overrides and isolates) is removed, not spaced, so codes stay exposed.
    private val INVISIBLE = Regex("""\p{Cf}""")
    private val SENDERS = setOf("MPESA", "M-PESA", "FULIZA")

    /**
     * Removes invisible format characters (Unicode Cf), collapses all Unicode whitespace to single spaces, trims.
     * Persisted as `sms.bodyHash` (UNIQUE dedupe key) in Phase 2 — changing either function requires a
     * migration that re-hashes every stored SMS.
     */
    fun normalize(body: String): String = body.replace(INVISIBLE, "").replace(WHITESPACE, " ").trim()

    /**
     * Lowercase hex SHA-256 of the normalised body. The `sms.bodyHash` dedupe key.
     * Persisted as `sms.bodyHash` (UNIQUE dedupe key) in Phase 2 — changing either function requires a
     * migration that re-hashes every stored SMS.
     */
    fun hash(body: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(normalize(body).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun isMpesaSender(address: String): Boolean = address.trim().uppercase() in SENDERS
}
