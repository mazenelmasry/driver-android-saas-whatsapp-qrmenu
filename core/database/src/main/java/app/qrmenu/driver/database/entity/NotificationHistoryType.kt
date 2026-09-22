package app.qrmenu.driver.database.entity

/**
 * What kind of thing landed in the notification centre. Stored as TEXT
 * (see [app.qrmenu.driver.database.NotificationHistoryTypeConverter]) rather
 * than an INTEGER ordinal — a reordered/inserted enum case must never
 * silently reinterpret an old row as a different type.
 *
 * Only [Offer] is ever written today: the only push the backend sends is
 * `type=driver_offer` (see `:core:push`'s `OfferPushPayload`). The enum
 * exists — rather than a bare boolean/string — so a second push type never
 * needs a Room migration to add, only a new case here.
 */
enum class NotificationHistoryType {
    Offer,
}
