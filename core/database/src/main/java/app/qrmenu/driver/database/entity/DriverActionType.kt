package app.qrmenu.driver.database.entity

/**
 * The trip commands a driver can queue offline. Kept as a real enum — not a
 * free-form string column — so a typo in a future caller fails to compile
 * instead of silently landing as a row the backend will never recognise.
 *
 * [Breadcrumbs] is a batch of trail points (`POST .../breadcrumbs`), not a
 * single-driver-tap command like the other three — see
 * [app.qrmenu.driver.location.upload.BreadcrumbOutbox]'s own doc for why it
 * still belongs in this same queue. Stored as `.name` in a plain TEXT column
 * ([app.qrmenu.driver.database.DriverActionTypeConverter]), so adding this
 * case needed no schema change / migration.
 */
enum class DriverActionType {
    PickedUp,
    Delivered,
    Issue,
    Breadcrumbs,
}
