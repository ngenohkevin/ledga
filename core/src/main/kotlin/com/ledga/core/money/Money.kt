package com.ledga.core.money

/** How many decimals [Money.amountText] shows. NEVER rounds half-up to whole shillings. */
enum class Decimals { AUTO, ALWAYS, NEVER }

/**
 * An amount of Kenyan shillings stored as integer cents. All arithmetic is
 * exact and overflow-checked; there is no floating point anywhere.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(cents, other.cents))
    operator fun minus(other: Money): Money = Money(Math.subtractExact(cents, other.cents))
    operator fun unaryMinus(): Money = Money(Math.negateExact(cents))
    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    val isZero: Boolean get() = cents == 0L
    val isNegative: Boolean get() = cents < 0L

    /** "1,200", "1,200.50" (AUTO), "1,200.00" (ALWAYS), "1,201" (NEVER, half-up). */
    fun amountText(decimals: Decimals = Decimals.AUTO): String {
        val abs = Math.absExact(cents)
        val frac = abs % 100
        val body = when (decimals) {
            Decimals.NEVER -> group(abs / 100 + if (frac >= 50) 1 else 0)
            Decimals.ALWAYS -> "${group(abs / 100)}.${pad2(frac)}"
            Decimals.AUTO -> if (frac == 0L) group(abs / 100) else "${group(abs / 100)}.${pad2(frac)}"
        }
        return if (cents < 0) "-$body" else body
    }

    fun kshText(decimals: Decimals = Decimals.AUTO): String = "Ksh " + amountText(decimals)

    /** Every spelling of this amount a user might search for (no sign). */
    fun searchForms(): List<String> {
        val abs = Math.absExact(cents)
        val whole = abs / 100
        val frac = pad2(abs % 100)
        val plain = whole.toString()
        val grouped = group(whole)
        val forms = if (abs % 100 == 0L) {
            listOf(plain, grouped, "$plain.$frac", "$grouped.$frac")
        } else {
            listOf("$plain.$frac", "$grouped.$frac")
        }
        return forms.distinct()
    }

    companion object {
        val ZERO = Money(0)

        private val GROUPED = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?""")
        private val PLAIN = Regex("""\d+(?:\.\d{1,2})?""")

        fun ofShillings(whole: Long, cents: Int = 0): Money {
            require(cents in 0..99) { "cents must be 0..99, was $cents" }
            return Money(Math.addExact(Math.multiplyExact(whole, 100L), cents.toLong()))
        }

        /**
         * Parses "1,200.00", "1200", "7360.55", "463.5". Returns null for anything
         * else, including signs, currency prefixes and badly grouped digits.
         */
        fun parse(text: String): Money? {
            val t = text.trim()
            if (!GROUPED.matches(t) && !PLAIN.matches(t)) return null
            val digits = t.replace(",", "")
            val dot = digits.indexOf('.')
            val wholePart = if (dot < 0) digits else digits.substring(0, dot)
            val fracPart = if (dot < 0) "" else digits.substring(dot + 1)
            if (wholePart.length > 15) return null
            val frac = when (fracPart.length) {
                0 -> 0L
                1 -> fracPart.toLong() * 10
                else -> fracPart.toLong()
            }
            return Money(wholePart.toLong() * 100 + frac)
        }

        private fun group(n: Long): String =
            n.toString().reversed().chunked(3).joinToString(",").reversed()

        private fun pad2(n: Long): String = n.toString().padStart(2, '0')
    }
}
