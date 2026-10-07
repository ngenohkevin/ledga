package com.ledga.app.data.trackers

import com.ledga.core.model.CategoryGroup

/**
 * What a category's numbers count (4e spec §3.3): spent, fees included, for the spending groups; what came in for Money in;
 * what moved, either way and without fees, for Not spending. Hidden and reversed rows count nothing in any of them.
 */
enum class CategoryMeasure {
    SPENT,
    RECEIVED,
    MOVED;

    companion object {
        fun of(group: CategoryGroup): CategoryMeasure = when (group) {
            CategoryGroup.MONEY_IN -> RECEIVED
            CategoryGroup.NOT_SPENDING -> MOVED
            CategoryGroup.BILLS_UTILITIES, CategoryGroup.CAR, CategoryGroup.EVERYDAY, CategoryGroup.MONEY -> SPENT
        }
    }
}
