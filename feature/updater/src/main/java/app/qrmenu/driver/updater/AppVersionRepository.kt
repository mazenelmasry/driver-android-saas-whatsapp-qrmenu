package app.qrmenu.driver.updater

import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.AppVersionDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * The one call this module makes. Thin on purpose: [check] never throws — see
 * its own doc — so nothing downstream of it needs its own try/catch to honour
 * "a failed check must never block anything."
 */
@Singleton
class AppVersionRepository @Inject constructor(
    private val authApi: AuthApi,
) {
    /**
     * `null` on ANY failure (no connection, timeout, 5xx, malformed body) —
     * deliberately swallowed rather than surfaced as [app.qrmenu.driver.network.errors.DriverApiError].
     * `driver/app-version` is advisory groundwork for a screen that must never
     * punish a driver for losing signal; the caller keeps the last successful
     * read on failure rather than tearing down a floor it already knows about.
     */
    suspend fun check(): AppVersionDto? = try {
        authApi.appVersion()
    } catch (e: CancellationException) {
        // Never swallow — this is structured-concurrency cancellation
        // (screen closed, process dying), not a network failure.
        throw e
    } catch (_: Throwable) {
        null
    }
}
