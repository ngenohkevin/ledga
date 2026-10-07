package com.ledga.app.data.alerts

/**
 * What an `alerts` row is about (spec §11, R71). `Notifier` writes `alerts.type` as one of these names (5a); any other
 * text (a newer version's type) reads as [OTHER], so an old build never crashes on a new log.
 */
enum class AlertType {
    LARGE,
    FULIZA_DRAW,
    FULIZA_DUE,
    DAILY,
    WEEKLY,
    OTHER,
    ;

    companion object {
        fun of(name: String): AlertType = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
