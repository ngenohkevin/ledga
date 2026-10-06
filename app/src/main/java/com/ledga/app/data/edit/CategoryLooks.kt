package com.ledga.app.data.edit

/**
 * What a category of the person's own can look like (R68, R74). Built-in categories keep their seeded icon and colour
 * token (spec §7.3). `TransactionEdits` accepts only these; the category screen draws them.
 */
object CategoryLooks {
    /** R43: what a new category starts with. */
    const val DEFAULT_ICON = "fluent_label"

    /** The label first, then the 16 vendored for this (`tools/design/fetch_fluent.py` `USER_CHOICE`). */
    val ICONS: List<String> = listOf(
        DEFAULT_ICON,
        "fluent_pill", "fluent_dog_face", "fluent_baby_bottle", "fluent_airplane",
        "fluent_books", "fluent_hot_beverage", "fluent_scissors", "fluent_seedling",
        "fluent_church", "fluent_hammer_and_wrench", "fluent_soccer_ball", "fluent_ticket",
        "fluent_motorcycle", "fluent_broom", "fluent_lipstick", "fluent_laptop",
    )

    /** A named colour, light and dark (`categories.color` / `colorDark`). The name is what TalkBack reads. */
    data class Swatch(val name: String, val light: String, val dark: String)

    val SWATCHES: List<Swatch> = listOf(
        Swatch("Teal", "#0E7C86", "#4FD1DB"),
        Swatch("Indigo", "#3F51B5", "#8C9EFF"),
        Swatch("Plum", "#8E3B8E", "#E28AE2"),
        Swatch("Coral", "#E0603E", "#FF9B80"),
        Swatch("Olive", "#6B7F1A", "#B5CC55"),
        Swatch("Rose", "#C2185B", "#FF7FAE"),
        Swatch("Amber", "#B7791F", "#F6C35B"),
        Swatch("Slate", "#546E7A", "#A7BDC8"),
        Swatch("Sky", "#0277BD", "#63C3FF"),
        Swatch("Forest", "#2E7D32", "#7BD67F"),
        Swatch("Brown", "#795548", "#C9A493"),
        Swatch("Violet", "#6A1B9A", "#C792EA"),
    )
}
