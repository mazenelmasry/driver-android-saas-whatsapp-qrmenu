package app.qrmenu.driver.network.errors

/**
 * The frozen machine-readable error codes from `openapi/driver.v1.yaml`
 * (`components.schemas.ErrorCode`).
 *
 * 🔴 The app branches and translates on THIS, never on `message`. The backend
 * speaks ar/en only, while a driver may be reading Urdu, Bengali or Hindi
 * (CLAUDE.md § أكواد الأخطاء) — rendering the server's sentence would show them
 * a language they do not read, and matching on its text would break the moment
 * a translator rewords it.
 *
 * Adding a case here without adding its string to all five translations is the
 * failure this enum exists to prevent, so the list is kept in the same order as
 * the contract and is covered by a test that compares it against the mirrored
 * spec.
 *
 * A code the server sends that is NOT in this list is deliberately NOT an enum
 * value: [fromWire] returns null, the caller shows a generic translated message
 * and reports it. An older app must not crash on a newer server's vocabulary.
 */
enum class DriverErrorCode(val wire: String) {
    InvalidCredentials("invalid_credentials"),
    OtpInvalid("otp_invalid"),
    OtpExpired("otp_expired"),
    TooManyAttempts("too_many_attempts"),
    InvalidInvite("invalid_invite"),
    InviteAlreadyUsed("invite_already_used"),
    AccountInactive("account_inactive"),
    NoActiveLink("no_active_link"),
    CompanyInactive("company_inactive"),
    SubscriptionExpired("subscription_expired"),
    FeatureNotInPlan("feature_not_in_plan"),
    AppUpdateRequired("app_update_required"),
    HasActiveTrip("has_active_trip"),
    DriverOffline("driver_offline"),
    LocationPermissionRequired("location_permission_required"),
    AlreadyClaimed("already_claimed"),
    OfferExpired("offer_expired"),
    TooManyActiveOrders("too_many_active_orders"),
    OrderNotReady("order_not_ready"),
    OrderCancelled("order_cancelled"),
    NotYourOrder("not_your_order"),
    CashLimitExceeded("cash_limit_exceeded"),
    /** Decision 48: `delivered` was sent with no `delivery_code` for an order that carries one. */
    DeliveryCodeRequired("delivery_code_required"),
    /** Decision 48: the four digits the driver entered do not match — never compared client-side. */
    DeliveryCodeMismatch("delivery_code_mismatch"),
    BranchClosed("branch_closed"),
    ValidationFailed("validation_failed"),
    RateLimited("rate_limited"),
    ServerError("server_error"),
    ;

    companion object {
        private val byWire: Map<String, DriverErrorCode> = entries.associateBy { it.wire }

        /** Null for a code this build does not know — see the class doc. */
        fun fromWire(value: String?): DriverErrorCode? = value?.let { byWire[it] }
    }
}
