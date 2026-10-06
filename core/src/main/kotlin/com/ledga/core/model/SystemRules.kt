package com.ledga.core.model

/** A seeded NAME_CONTAINS rule. Patterns are matched on whole words (see RuleEngine). */
data class SystemRuleSeed(val pattern: String, val categoryKey: String)

object SystemRules {
    private fun rules(category: String, vararg patterns: String) = patterns.map { SystemRuleSeed(it, category) }

    /** Order = seed id order = match order among SYSTEM rules. Curated against the owner's corpus (Phase 1). */
    val SEED: List<SystemRuleSeed> =
        rules(Categories.ELECTRICITY, "KPLC", "KENYA POWER") +
            rules(Categories.WATER, "NAIROBI WATER", "WATER AND SEWERAGE", "WATER & SEWERAGE", "WATER SERVICES") +
            rules(
                Categories.FUEL, "RUBIS", "TOTALENERGIES", "TOTAL ENERGIES", "SHELL", "VIVO ENERGY", "OLA ENERGY",
                "ASTROL", "GULF ENERGY", "HASS PETROLEUM", "SERVICE STATION", "PETROL STATION",
            ) +
            rules(Categories.CAR_SERVICE, "GARAGE", "AUTO SPARES", "AUTOSPARES", "TYRE", "TYRES") +
            rules(Categories.GROCERIES, "NAIVAS", "QUICKMART", "CARREFOUR", "CLEANSHELF", "CHANDARANA") +
            rules(Categories.FOOD, "JAVA", "KFC", "CHICKEN INN", "PIZZA INN", "ARTCAFFE", "GALITOS") +
            rules(Categories.TRANSPORT, "UBER", "BOLT", "LITTLE CAB") +
            rules(Categories.AIRTIME_DATA, "DATA BUNDLES", "SAFARICOM DATA", "AIRTIME", "SAFARICOM OFFERS") +
            rules(Categories.INTERNET, "ZUKU", "FAIBA", "STARLINK", "SAFARICOM HOME", "JAMII TELECOMMUNICATIONS") +
            rules(Categories.TV, "DSTV", "GOTV", "SHOWMAX", "NETFLIX", "STARTIMES") +
            rules(Categories.LOANS_CREDIT, "HUSTLER FUND", "TALA", "BRANCH MICROFINANCE", "BRANCH INTERNATIONAL", "ZENKA") +
            // Added after Phase 1 (owner, 2026-10-06): county water companies. Appended, so earlier seed ids stay put.
            rules(Categories.WATER, "ELDOWAS", "WATER AND SANITATION", "WATER & SANITATION")
}
