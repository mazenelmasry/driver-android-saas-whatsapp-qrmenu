package app.qrmenu.driver.account

import app.qrmenu.driver.network.dto.DriverDto
import app.qrmenu.driver.network.dto.RestaurantLinkDto
import app.qrmenu.driver.network.errors.DriverApiError

/**
 * «حسابى» — the driver's own identity, the restaurants they work for, and the
 * device-local preferences (language, size, battery) that used to have no
 * home in the tab bar at all.
 *
 * The restaurant list is fetched from the same `GET /driver/me` call
 * `:feature:home`'s `HomeViewModel` used before this screen replaced it — see
 * that module's doc for why [restaurants] is a list, not a single company
 * (decision 19), and why an empty list with a non-null [driver] is a real,
 * named state rather than a blank screen (decision 47).
 */
data class AccountUiState(
    /** True only for the FIRST load, before any data has ever arrived — drives the skeleton. */
    val isLoading: Boolean = true,
    /** True while a pull-to-refresh is in flight on top of data already on screen. */
    val isRefreshing: Boolean = false,
    val driver: DriverDto? = null,
    val restaurants: List<RestaurantLinkDto> = emptyList(),
    val error: DriverApiError? = null,
) {
    /**
     * A verified phone with zero restaurants is a REAL, expected state (a
     * driver who proved their number but has not been invited yet) — never a
     * blank list. Guarded on [driver] so a failed FIRST load (no data at all)
     * is never mistaken for "confirmed empty".
     */
    val isEmpty: Boolean
        get() = !isLoading && driver != null && restaurants.isEmpty()
}
