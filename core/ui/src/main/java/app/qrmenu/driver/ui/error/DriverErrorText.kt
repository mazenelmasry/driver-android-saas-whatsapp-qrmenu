package app.qrmenu.driver.ui.error

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.network.errors.PhoneVerificationFailureReason
import app.qrmenu.driver.ui.R

/**
 * Turns a failure into a sentence in the driver's language.
 *
 * 🔴 The mapping is on the CODE, never on the server's `message`. The backend
 * speaks Arabic and English only, while a driver may be reading Urdu, Bengali or
 * Hindi — so `message` is for logs and for Sentry, and showing it would hand a
 * Bengali-reading driver an Arabic sentence at the exact moment something went
 * wrong (CLAUDE.md, error codes).
 *
 * A code this build does not know maps to [R.string.error_unknown], the generic
 * translated line — never to the raw text. Same for an unknown HTTP status.
 * That is what lets an older app meet a newer server without showing nonsense.
 */
@Composable
fun DriverApiError.localized(): String = stringResource(messageResource())

/** The resource alone, for a ViewModel that needs to store the failure rather than draw it. */
@StringRes
fun DriverApiError.messageResource(): Int = when (this) {
    // A phone in a moving car loses signal routinely. It reads differently from
    // a server refusal on purpose: one is the road, the other is the account.
    is DriverApiError.Offline -> R.string.error_network_unavailable
    // The server sent something these DTOs cannot read — a contract break, not
    // something the driver can fix or retry away.
    is DriverApiError.Decode -> R.string.error_server_error
    is DriverApiError.Unknown -> R.string.error_unknown
    is DriverApiError.Api -> code?.messageResource() ?: R.string.error_unknown
    is DriverApiError.PhoneVerification -> reason.messageResource()
}

@StringRes
private fun PhoneVerificationFailureReason.messageResource(): Int = when (this) {
    PhoneVerificationFailureReason.Unavailable -> R.string.error_phone_verification_unavailable
    PhoneVerificationFailureReason.ConfigError -> R.string.error_phone_verification_config_error
    PhoneVerificationFailureReason.Cancelled -> R.string.error_phone_verification_cancelled
    PhoneVerificationFailureReason.Blocked -> R.string.error_phone_verification_blocked
}

@StringRes
fun DriverErrorCode.messageResource(): Int = when (this) {
    DriverErrorCode.InvalidCredentials -> R.string.error_invalid_credentials
    DriverErrorCode.NoDriverAccount -> R.string.error_no_driver_account
    DriverErrorCode.OtpInvalid -> R.string.error_otp_invalid
    DriverErrorCode.OtpExpired -> R.string.error_otp_expired
    DriverErrorCode.TooManyAttempts -> R.string.error_too_many_attempts
    DriverErrorCode.InvalidInvite -> R.string.error_invalid_invite
    DriverErrorCode.InviteAlreadyUsed -> R.string.error_invite_already_used
    DriverErrorCode.AccountInactive -> R.string.error_account_inactive
    DriverErrorCode.NoActiveLink -> R.string.error_no_active_link
    DriverErrorCode.CompanyInactive -> R.string.error_company_inactive
    DriverErrorCode.SubscriptionExpired -> R.string.error_subscription_expired
    DriverErrorCode.FeatureNotInPlan -> R.string.error_feature_not_in_plan
    DriverErrorCode.AppUpdateRequired -> R.string.error_app_update_required
    DriverErrorCode.HasActiveTrip -> R.string.error_has_active_trip
    DriverErrorCode.DriverOffline -> R.string.error_driver_offline
    DriverErrorCode.LocationPermissionRequired -> R.string.error_location_permission_required
    DriverErrorCode.AlreadyClaimed -> R.string.error_already_claimed
    DriverErrorCode.OfferExpired -> R.string.error_offer_expired
    DriverErrorCode.TooManyActiveOrders -> R.string.error_too_many_active_orders
    DriverErrorCode.OrderNotReady -> R.string.error_order_not_ready
    DriverErrorCode.OrderCancelled -> R.string.error_order_cancelled
    DriverErrorCode.NotYourOrder -> R.string.error_not_your_order
    DriverErrorCode.CashLimitExceeded -> R.string.error_cash_limit_exceeded
    DriverErrorCode.DeliveryCodeRequired -> R.string.error_delivery_code_required
    DriverErrorCode.DeliveryCodeMismatch -> R.string.error_delivery_code_mismatch
    DriverErrorCode.BranchClosed -> R.string.error_branch_closed
    DriverErrorCode.ValidationFailed -> R.string.error_validation_failed
    DriverErrorCode.RateLimited -> R.string.error_rate_limited
    DriverErrorCode.ServerError -> R.string.error_server_error
}

/**
 * True when retrying the same request could plausibly work.
 *
 * Drives whether a "try again" button is offered at all: showing one next to
 * "your account is suspended" teaches a driver that the button does nothing.
 */
val DriverApiError.isRetryable: Boolean
    get() = when (this) {
        is DriverApiError.Offline -> true
        is DriverApiError.Unknown -> true
        is DriverApiError.Decode -> false
        is DriverApiError.Api -> when (code) {
            DriverErrorCode.RateLimited,
            DriverErrorCode.ServerError,
            null,
            -> true
            else -> false
        }
        // The three transient local Firebase failures — a fresh send attempt
        // is exactly the right recovery. `ConfigError` is deliberately
        // excluded: retrying a fingerprint mismatch does nothing.
        is DriverApiError.PhoneVerification -> reason != PhoneVerificationFailureReason.ConfigError
    }
