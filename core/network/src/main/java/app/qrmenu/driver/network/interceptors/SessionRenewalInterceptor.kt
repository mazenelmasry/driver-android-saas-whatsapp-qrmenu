package app.qrmenu.driver.network.interceptors

import app.qrmenu.driver.common.session.TokenExpiry
import app.qrmenu.driver.datastore.TokenStore
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * The client half of "the token lives 30 days and renews on use"
 * (`driverToken` security scheme, `openapi/driver.v1.yaml`).
 *
 * 🔴 An earlier version of this pushed the LOCAL deadline forward by a flat
 * 30 days on its own, without the backend telling it to. That was rejected:
 * `driver_tokens.expires_at` on the server is a fixed deadline set once at
 * issue — nothing there was sliding it — so the app would have believed a
 * session was good for another month while the server was about to answer
 * every request 401. The real fix is server-side (`DriverAuth::handle()`
 * slides the deadline itself, within `driver.token_renew_within_days` of it,
 * on a request that authenticates successfully) — this interceptor's ONLY
 * job now is to keep the local copy in sync with what the server just
 * reported, never to invent an extension of its own.
 *
 * Every response behind `driver.auth` carries the CURRENT deadline in
 * `X-Driver-Token-Expires-At` (see the header's own doc in the YAML),
 * whether or not that particular request happened to renew it server-side.
 * This interceptor reads it unconditionally and writes it to [TokenStore]
 * only when it actually differs from what is already stored — most
 * responses report the same deadline the last one did, and a
 * `SharedPreferences` write for an unchanged value would be pure waste.
 */
class SessionRenewalInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val wasSigned = request.tag(PublicEndpoint::class.java) == null &&
            request.header("Authorization") != null

        if (wasSigned && response.isSuccessful) {
            val reported = TokenExpiry.parseExpiresAt(response.header(EXPIRES_AT_HEADER))
            if (reported != null && reported != tokenStore.expiresAt.value) {
                tokenStore.touchExpiry(reported)
            }
        }

        return response
    }

    private companion object {
        const val EXPIRES_AT_HEADER = "X-Driver-Token-Expires-At"
    }
}
