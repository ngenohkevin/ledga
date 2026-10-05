package com.ledga.core.model

enum class CategoryGroup(val displayName: String) {
    BILLS_UTILITIES("Bills & utilities"),
    CAR("Car"),
    EVERYDAY("Everyday"),
    MONEY("Money"),
    MONEY_IN("Money in"),
    NOT_SPENDING("Not spending");

    /** Whether a category in this group may be assigned (by a rule) to a transaction with [flow]. */
    fun accepts(flow: FlowKind): Boolean = when (this) {
        BILLS_UTILITIES, CAR, EVERYDAY, MONEY -> flow == FlowKind.SPEND
        MONEY_IN -> flow == FlowKind.INCOME
        NOT_SPENDING -> flow != FlowKind.SPEND && flow != FlowKind.INCOME
    }
}

/** A seeded system category. [icon3d] is the Fluent Emoji 3D asset name (res/drawable-nodpi, Phase 3). */
data class CategorySeed(
    val key: String,
    val name: String,
    val group: CategoryGroup,
    val icon3d: String,
    val tracked: Boolean,
    val sortOrder: Int,
)

object Categories {
    const val ELECTRICITY = "electricity"
    const val WATER = "water"
    const val INTERNET = "internet"
    const val TV = "tv"
    const val RENT = "rent"
    const val FUEL = "fuel"
    const val CAR_SERVICE = "car_service"
    const val PARKING = "parking"
    const val GROCERIES = "groceries"
    const val FOOD = "food"
    const val TRANSPORT = "transport"
    const val AIRTIME_DATA = "airtime_data"
    const val SHOPPING = "shopping"
    const val HEALTH = "health"
    const val SCHOOL = "school"
    const val SENT_TO_PEOPLE = "sent_to_people"
    const val CASH_WITHDRAWAL = "cash_withdrawal"
    const val LOANS_CREDIT = "loans_credit"
    const val INTERNATIONAL = "international"
    const val OTHER = "other"
    const val RECEIVED = "received"
    const val CASH_DEPOSIT = "cash_deposit"
    const val OTHER_INCOME = "other_income"
    const val SAVINGS = "savings"
    const val OWN_ACCOUNTS = "own_accounts"
    const val FULIZA = "fuliza"
    const val REVERSALS = "reversals"

    private data class Row(val key: String, val name: String, val group: CategoryGroup, val icon: String, val tracked: Boolean = false)

    private val ROWS = listOf(
        Row(ELECTRICITY, "Electricity", CategoryGroup.BILLS_UTILITIES, "high_voltage", tracked = true),
        Row(WATER, "Water", CategoryGroup.BILLS_UTILITIES, "droplet", tracked = true),
        Row(INTERNET, "Internet", CategoryGroup.BILLS_UTILITIES, "antenna_bars"),
        Row(TV, "TV", CategoryGroup.BILLS_UTILITIES, "television"),
        Row(RENT, "Rent", CategoryGroup.BILLS_UTILITIES, "house"),
        Row(FUEL, "Fuel", CategoryGroup.CAR, "fuel_pump", tracked = true),
        Row(CAR_SERVICE, "Car service", CategoryGroup.CAR, "wrench", tracked = true),
        Row(PARKING, "Parking", CategoryGroup.CAR, "p_button"),
        Row(GROCERIES, "Groceries", CategoryGroup.EVERYDAY, "shopping_cart"),
        Row(FOOD, "Food", CategoryGroup.EVERYDAY, "fork_and_knife_with_plate"),
        Row(TRANSPORT, "Transport", CategoryGroup.EVERYDAY, "bus"),
        Row(AIRTIME_DATA, "Airtime & data", CategoryGroup.EVERYDAY, "mobile_phone"),
        Row(SHOPPING, "Shopping", CategoryGroup.EVERYDAY, "shopping_bags"),
        Row(HEALTH, "Health", CategoryGroup.EVERYDAY, "hospital"),
        Row(SCHOOL, "School", CategoryGroup.EVERYDAY, "graduation_cap"),
        Row(SENT_TO_PEOPLE, "Sent to people", CategoryGroup.MONEY, "outbox_tray"),
        Row(CASH_WITHDRAWAL, "Cash withdrawal", CategoryGroup.MONEY, "dollar_banknote"),
        Row(LOANS_CREDIT, "Loans & credit", CategoryGroup.MONEY, "money_with_wings"),
        Row(INTERNATIONAL, "International", CategoryGroup.MONEY, "globe_with_meridians"),
        Row(OTHER, "Other", CategoryGroup.MONEY, "package"),
        Row(RECEIVED, "Received", CategoryGroup.MONEY_IN, "inbox_tray"),
        Row(CASH_DEPOSIT, "Cash deposit", CategoryGroup.MONEY_IN, "coin"),
        Row(OTHER_INCOME, "Other income", CategoryGroup.MONEY_IN, "wrapped_gift"),
        Row(SAVINGS, "Savings", CategoryGroup.NOT_SPENDING, "money_bag"),
        Row(OWN_ACCOUNTS, "Own accounts", CategoryGroup.NOT_SPENDING, "bank"),
        Row(FULIZA, "Fuliza", CategoryGroup.NOT_SPENDING, "credit_card"),
        Row(REVERSALS, "Reversals", CategoryGroup.NOT_SPENDING, "repeat_button"),
    )

    val SEED: List<CategorySeed> = ROWS.mapIndexed { i, r ->
        CategorySeed(r.key, r.name, r.group, "fluent_${r.icon}", r.tracked, i)
    }

    private val BY_KEY = SEED.associateBy { it.key }

    fun seed(key: String): CategorySeed? = BY_KEY[key]
}

object KindDefaults {
    fun categoryFor(kind: TxKind): String = when (kind) {
        TxKind.SEND -> Categories.SENT_TO_PEOPLE
        TxKind.PAYBILL, TxKind.BUY_GOODS, TxKind.FULIZA_ONLY -> Categories.OTHER
        TxKind.WITHDRAW_AGENT, TxKind.WITHDRAW_ATM -> Categories.CASH_WITHDRAWAL
        TxKind.DEPOSIT -> Categories.CASH_DEPOSIT
        TxKind.RECEIVE, TxKind.GLOBAL_RECEIVE -> Categories.RECEIVED
        TxKind.AIRTIME_SELF, TxKind.AIRTIME_OTHER -> Categories.AIRTIME_DATA
        TxKind.GLOBAL_SEND -> Categories.INTERNATIONAL
        TxKind.SAVINGS_OUT, TxKind.SAVINGS_IN -> Categories.SAVINGS
        TxKind.FULIZA_REPAY_AUTO, TxKind.FULIZA_REPAY_MANUAL -> Categories.FULIZA
        TxKind.REVERSAL, TxKind.FULIZA_REVERSAL -> Categories.REVERSALS
    }
}
