package app.qrmenu.driver.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per push that reached this device — the notification centre's
 * data source and the source of the unread badge on [app.qrmenu.driver.ui
 * .components.DriverHeader]'s bell.
 *
 * 🔴 Deliberately holds NO title/body text. The screen renders those from
 * `feature/notifications`' string resources at read time, interpolating
 * [order_id] — the same reason `driver_actions_outbox` holds no customer
 * phone/address (CLAUDE.md's "الاحتفاظ بالبيانات"/language rules): a driver
 * can switch the app's language after a notification arrives, and a
 * literal string baked in at write time would freeze in whatever language
 * was active when the push landed instead of following the switch.
 *
 * [offer_id] is kept alongside [order_id] purely for future debugging/
 * navigation (e.g. "which specific offer was this") — nothing reads it yet.
 *
 * Retention: capped at [app.qrmenu.driver.database.NotificationHistoryDao
 * .MAX_HISTORY_ROWS] rows and [app.qrmenu.driver.database
 * .NotificationHistoryDao.RETENTION_WINDOW_MILLIS], pruned on every insert
 * (see [app.qrmenu.driver.database.NotificationHistoryDao.insertAndPrune]) —
 * this is local, disposable history, not the financial ledger, so there is
 * no server copy to reconcile against and nothing wrong with trimming it.
 */
@Entity(
    tableName = "notification_history",
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["is_read"]),
    ],
)
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: NotificationHistoryType,
    val order_id: Long?,
    val offer_id: Long?,
    // Epoch millis at the moment the push was handled on this device —
    // there is no server-issued timestamp to prefer (see OfferPushPayload:
    // `expires_at` is a deadline, not an arrival time).
    val occurred_at: Long,
    val is_read: Boolean = false,
)
