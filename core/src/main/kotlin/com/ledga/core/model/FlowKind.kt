package com.ledga.core.model

/**
 * How a transaction's money is treated. Assigned once at derivation.
 * Only SPEND counts as spending and only INCOME counts as money in;
 * everything else is a movement that is neither.
 */
enum class FlowKind { SPEND, INCOME, SAVINGS_OUT, SAVINGS_IN, OWN_OUT, OWN_IN, LOAN_REPAY, REVERSAL_IN }
