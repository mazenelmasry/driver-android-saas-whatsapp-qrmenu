package app.qrmenu.driver.database.entity

/**
 * The trip commands a driver can queue offline. Kept as a real enum — not a
 * free-form string column — so a typo in a future caller fails to compile
 * instead of silently landing as a row the backend will never recognise.
 */
enum class DriverActionType {
    PickedUp,
    Delivered,
    Issue,
}
