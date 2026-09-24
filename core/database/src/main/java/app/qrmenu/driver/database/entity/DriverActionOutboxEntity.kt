package app.qrmenu.driver.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The offline queue for trip commands (picked-up / delivered / issue) taken
 * while unreachable or retried after a failure. A row here is unsent,
 * possibly money-affecting driver state (e.g. cash collected on a `Delivered`
 * action, not yet acknowledged by the server) — see CLAUDE.md's "الحالات
 * الحدّية" (app-close mid-trip, device-change flush) and "الاحتفاظ بالبيانات"
 * tables. That is also why this table is never covered by
 * `fallbackToDestructiveMigration()`.
 *
 * [idempotency_key] IS the primary key rather than a separate concern: the
 * server's `Idempotency-Key` header for this action must be exactly the value
 * that survives a process death, so a lost outbox row and a lost idempotency
 * key are the same failure — there is nothing to keep in sync between two
 * columns if there is only one. It is generated client-side (UUID) when the
 * action is first taken, before any network call.
 *
 * 🔴 Deliberately absent: the customer's phone number or address. CLAUDE.md's
 * data-retention table requires those to be purged from the device
 * immediately after delivery — an outbox row that outlives the delivery (a
 * retry, a queued send) must not be the thing that keeps them alive. Only
 * [order_id] is kept; whatever the backend still needs about the order is its
 * own problem to look up, not this table's to cache.
 *
 * [driver_id] — added in schema v3 — is who took the action, not who is
 * currently signed in. On a shared device, sign-out never clears this table
 * (a queued `delivered` may still be unsent money), so without a stamped
 * owner a second driver's session would inherit and replay the first
 * driver's queued commands under their own token, which the server answers
 * with 404 (order not theirs) — and [isPureRejection]'s existing 4xx handling
 * would then DELETE that row, permanently losing driver A's delivery/cash
 * record. `null` means "queued before this column existed" and keeps today's
 * behaviour (replayed by whoever is signed in) — see
 * [app.qrmenu.driver.trip.TripRepository]'s own doc on where the filtering
 * happens.
 */
@Entity(
    tableName = "driver_actions_outbox",
    indices = [Index(value = ["order_id"])],
)
data class DriverActionOutboxEntity(
    @PrimaryKey val idempotency_key: String,
    val order_id: Long,
    val action_type: DriverActionType,
    // The request body this action will POST, serialised — cash_collected,
    // note, etc. Never the customer's phone/address (see class doc).
    val payload_json: String,
    // Device-clamped per the contract: (now - 6h) .. now at the moment the
    // action was taken. CLAUDE.md: "occurred_at مقصوص بين (الآن − ٦ ساعات)
    // والآن، والاثنان يُخزَّنان" — both this and [created_at] are kept because
    // they answer different questions (when it *happened* vs. when it was
    // *queued* — a device that was offline for an hour has both differ).
    val occurred_at: Long,
    val created_at: Long,
    val attempts: Int = 0,
    val last_error: String? = null,
    // Null = legacy row queued before this column existed, or a device where
    // the driver id wasn't yet known — always replayed regardless of who is
    // signed in now (see class doc).
    val driver_id: Long? = null,
)
