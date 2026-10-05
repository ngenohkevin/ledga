package com.ledga.core.parse

import java.security.MessageDigest

object SmsText {
    // Java's \s does not include no-break spaces; some ROMs and backup tools insert them.
    private val WHITESPACE = Regex("[\\s\\u00A0\\u2007\\u202F]+")
    private val SENDERS = setOf("MPESA", "M-PESA", "FULIZA")

    fun normalize(body: String): String = body.replace(WHITESPACE, " ").trim()

    /** Lowercase hex SHA-256 of the normalised body. The `sms.bodyHash` dedupe key. */
    fun hash(body: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(normalize(body).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun isMpesaSender(address: String): Boolean = address.trim().uppercase() in SENDERS
}
