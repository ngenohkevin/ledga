package com.ledga.core.derive

/**
 * A transaction is reversed when another transaction's `reversesCode` equals its code.
 * Phase 2 recomputes `isReversed` for affected codes in SQL; this is the shared rule.
 */
object Reversals {
    fun reversedCodes(reversesCodes: Iterable<String?>): Set<String> = reversesCodes.filterNotNull().toSet()
}
