package com.ledga.app.ui.design.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.ledga.core.model.Categories

/** A category's colour in each theme. Used only for charts, legends and progress fills, never for text (spec §10.1). */
@Immutable
data class CategoryColor(val light: Color, val dark: Color) {
    fun pick(dark: Boolean): Color = if (dark) this.dark else light
}

/**
 * Category colours keyed by category key. Refinement R9: a seeded category stores no colour and takes its token
 * from here; a USER category (v1 custom) stores its own `#RRGGBB`. R21 lists the 12 colours the mockups didn't cover.
 */
object CategoryPalette {

    /** An unknown key with no usable stored colour. */
    val NEUTRAL = CategoryColor(Color(0xFF6B7672), Color(0xFF8B9792))

    private fun c(light: Long, dark: Long) = CategoryColor(Color(light), Color(dark))

    private val SEEDED: Map<String, CategoryColor> = mapOf(
        Categories.ELECTRICITY to c(0xFFD98A00, 0xFFFFC24D),
        Categories.WATER to c(0xFF1E7FD8, 0xFF5CB2FF),
        Categories.INTERNET to c(0xFF8A4FC7, 0xFFC29BFF),
        Categories.TV to c(0xFFC93A5E, 0xFFFF7F9E),
        Categories.RENT to c(0xFF3F8F3A, 0xFF79D272),
        Categories.FUEL to c(0xFFE4572E, 0xFFFF8463),
        Categories.CAR_SERVICE to c(0xFF5B6B8C, 0xFF9FB0D6),
        Categories.PARKING to c(0xFF7A9A1A, 0xFFB8D65A),
        Categories.GROCERIES to c(0xFF1F9D55, 0xFF4FD68A),
        Categories.FOOD to c(0xFFE0457B, 0xFFFF7FAA),
        Categories.TRANSPORT to c(0xFF2F6FEB, 0xFF6E9BFF),
        Categories.AIRTIME_DATA to c(0xFF8E4FE0, 0xFFB98BFF),
        Categories.SHOPPING to c(0xFFF06A35, 0xFFFF9A6E),
        Categories.HEALTH to c(0xFF0F9AA8, 0xFF4FD3DE),
        Categories.SCHOOL to c(0xFFA39100, 0xFFE3D04A),
        Categories.SENT_TO_PEOPLE to c(0xFF5F6F82, 0xFFA6B4C4),
        Categories.CASH_WITHDRAWAL to c(0xFF9A6235, 0xFFD69A6B),
        Categories.LOANS_CREDIT to c(0xFFB23A7A, 0xFFF07AB6),
        Categories.INTERNATIONAL to c(0xFF4F58D9, 0xFF8D94FF),
        Categories.OTHER to c(0xFF8A7A68, 0xFFC2B3A0),
        Categories.RECEIVED to c(0xFF0E9F8A, 0xFF3FD6BF),
        Categories.CASH_DEPOSIT to c(0xFF2E8B3E, 0xFF6BD47E),
        Categories.OTHER_INCOME to c(0xFF6E56CF, 0xFFA895FF),
        Categories.SAVINGS to c(0xFF0B8F84, 0xFF3DD3C5),
        Categories.OWN_ACCOUNTS to c(0xFF3B6EA8, 0xFF86B2E6),
        Categories.FULIZA to c(0xFFD0414B, 0xFFFF7D86),
        Categories.REVERSALS to c(0xFF7C6F64, 0xFFB8AA9C),
    )

    fun seeded(key: String): CategoryColor? = SEEDED[key]

    /**
     * The colour for a category row:
     * 1. A stored light colour wins. If no dark one is stored, its dark variant is mixed 35 % towards white.
     * 2. Otherwise the seeded token for the key.
     * 3. Otherwise [NEUTRAL].
     *
     * Malformed text never throws: it is treated as absent.
     */
    fun resolve(key: String, color: String?, colorDark: String?): CategoryColor {
        val light = parseHex(color)
        val dark = parseHex(colorDark)
        val base = SEEDED[key] ?: NEUTRAL
        return when {
            light != null -> CategoryColor(light, dark ?: lift(light))
            dark != null -> CategoryColor(base.light, dark)
            else -> base
        }
    }


    internal fun lift(color: Color): Color = lerp(color, Color.White, 0.35f)

    /** `#RRGGBB` or `#AARRGGBB` (alpha ignored: a category colour is always opaque); anything else is null. */
    internal fun parseHex(text: String?): Color? {
        val hex = text?.trim()?.removePrefix("#") ?: return null
        if (hex.length != 6 && hex.length != 8) return null
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return Color(0xFF000000L or hex.takeLast(6).toLong(16))
    }
}
