package com.ledga.core.parse

import java.security.MessageDigest

object SmsText {
    // Java's \s does not include no-break spaces; some ROMs and backup tools insert them.
    private val WHITESPACE = Regex("[\\s\\u00A0\\u2007\\u202F]+")
    // BOM, zero-width space/non-joiner/joiner and word joiner: invisible, removed (not spaced) so codes stay exposed.
    private val ZERO_WIDTH = Regex("[\uFEFF\u200B\u200C\u200D\u2060]")
    private val SENDERS = setOf("MPESA", "M-PESA", "FULIZA")

    /**
     * Removes zero-width characters, collapses whitespace, trims.
     * Persisted as `sms.bodyHash` (UNIQUE dedupe key) in Phase 2 — changing either function requires a
     * migration that re-hashes every stored SMS.
     */
    fun normalize(body: String): String = body.replace(ZERO_WIDTH, "").replace(WHITESPACE, " ").trim()

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
