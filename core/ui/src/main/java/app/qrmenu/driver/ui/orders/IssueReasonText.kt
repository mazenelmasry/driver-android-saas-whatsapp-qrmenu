package app.qrmenu.driver.ui.orders

import androidx.annotation.StringRes
import app.qrmenu.driver.network.dto.DriverIssueCode
import app.qrmenu.driver.ui.R

/**
 * The five reasons a driver can report on `POST .../issue`
 * (`openapi/driver.v1.yaml` § `issue.code`), in the driver's language.
 *
 * Unlike [NoOrdersReason] this vocabulary is never PARSED from the wire — the
 * driver picks one of exactly five values to SEND — so there is no `Unknown`
 * case and no `fromWire`: [DriverIssueCode] is already exhaustive by
 * construction, and every case here is a compile error away from drifting out
 * of sync with it.
 *
 * Reporting an issue deliberately does not move the order (it is information
 * for the restaurant, not a status change the driver gets to decide), so this
 * text belongs next to a "report a problem" action, never next to a status
 * label.
 */
@StringRes
fun DriverIssueCode.messageResource(): Int = when (this) {
    DriverIssueCode.CustomerUnreachable -> R.string.issue_reason_customer_unreachable
    DriverIssueCode.AddressWrong -> R.string.issue_reason_address_wrong
    DriverIssueCode.CustomerRefused -> R.string.issue_reason_customer_refused
    DriverIssueCode.VehicleProblem -> R.string.issue_reason_vehicle_problem
    DriverIssueCode.Other -> R.string.issue_reason_other
}
