package com.ledga.app.data.db

import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.CategoryRule
import com.ledga.app.data.db.entity.MatchType

object DefaultData {

    val DEFAULT_CATEGORIES = listOf(
        Category(id = 1, name = "Groceries", icon = "shopping_cart", color = "#4CAF50"),
        Category(id = 2, name = "Transport", icon = "directions_car", color = "#2196F3"),
        Category(id = 3, name = "Bills & Utilities", icon = "receipt", color = "#FF9800"),
        Category(id = 4, name = "Airtime & Data", icon = "phone_android", color = "#9C27B0"),
        Category(id = 5, name = "Food & Dining", icon = "restaurant", color = "#E91E63"),
        Category(id = 6, name = "Send Money", icon = "person", color = "#607D8B"),
        Category(id = 7, name = "Received", icon = "account_balance_wallet", color = "#00BCD4"),
        Category(id = 8, name = "Withdrawal", icon = "atm", color = "#795548"),
        Category(id = 9, name = "Deposit", icon = "savings", color = "#8BC34A"),
        Category(id = 10, name = "Shopping", icon = "shopping_bag", color = "#FF5722"),
        Category(id = 11, name = "International", icon = "public", color = "#3F51B5"),
        Category(id = 12, name = "Savings & Loans", icon = "account_balance", color = "#009688"),
        Category(id = 13, name = "Other", icon = "category", color = "#9E9E9E"),
    )

    val DEFAULT_RULES = listOf(
        // Groceries (id=1)
        CategoryRule(categoryId = 1, matchType = MatchType.RECIPIENT_NAME, matchValue = "NAIVAS"),
        CategoryRule(categoryId = 1, matchType = MatchType.RECIPIENT_NAME, matchValue = "QUICKMART"),
        CategoryRule(categoryId = 1, matchType = MatchType.RECIPIENT_NAME, matchValue = "CARREFOUR"),
        CategoryRule(categoryId = 1, matchType = MatchType.RECIPIENT_NAME, matchValue = "CLEANSHELF"),

        // Transport (id=2)
        CategoryRule(categoryId = 2, matchType = MatchType.RECIPIENT_NAME, matchValue = "UBER"),
        CategoryRule(categoryId = 2, matchType = MatchType.RECIPIENT_NAME, matchValue = "BOLT"),
        CategoryRule(categoryId = 2, matchType = MatchType.RECIPIENT_NAME, matchValue = "LITTLE"),

        // Bills & Utilities (id=3)
        CategoryRule(categoryId = 3, matchType = MatchType.RECIPIENT_NAME, matchValue = "KPLC"),
        CategoryRule(categoryId = 3, matchType = MatchType.RECIPIENT_NAME, matchValue = "KENYA POWER"),
        CategoryRule(categoryId = 3, matchType = MatchType.PAYBILL, matchValue = "888880"),
        CategoryRule(categoryId = 3, matchType = MatchType.PAYBILL, matchValue = "888888"),
        CategoryRule(categoryId = 3, matchType = MatchType.RECIPIENT_NAME, matchValue = "NAIROBI WATER"),
        CategoryRule(categoryId = 3, matchType = MatchType.PAYBILL, matchValue = "444400"),
        CategoryRule(categoryId = 3, matchType = MatchType.RECIPIENT_NAME, matchValue = "DSTV"),
        CategoryRule(categoryId = 3, matchType = MatchType.RECIPIENT_NAME, matchValue = "GOTV"),
        CategoryRule(categoryId = 3, matchType = MatchType.RECIPIENT_NAME, matchValue = "SHOWMAX"),

        // Food & Dining (id=5)
        CategoryRule(categoryId = 5, matchType = MatchType.RECIPIENT_NAME, matchValue = "JAVA"),
        CategoryRule(categoryId = 5, matchType = MatchType.RECIPIENT_NAME, matchValue = "KFC"),
        CategoryRule(categoryId = 5, matchType = MatchType.RECIPIENT_NAME, matchValue = "CHICKEN INN"),
        CategoryRule(categoryId = 5, matchType = MatchType.RECIPIENT_NAME, matchValue = "PIZZA INN"),
    )
}
