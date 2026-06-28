package com.ledga.app.data.db.entity

/**
 * An optional, user-applied tag marking a transaction as a car expense.
 *
 * This is a SEPARATE dimension from [Category]: a fuel purchase keeps its
 * normal category (e.g. Transport) AND carries a [CarTag], so car spending can
 * be totalled on its own without disturbing the regular category breakdown.
 * `null` on a transaction means "not a car expense" — the common case.
 */
enum class CarTag(val label: String) {
    FUEL("Fuel"),
    SERVICE("Service"),
}
