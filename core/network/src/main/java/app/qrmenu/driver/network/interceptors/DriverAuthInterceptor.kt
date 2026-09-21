package app.qrmenu.driver.network.interceptors

import app.qrmenu.driver.datastore.TokenStore
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <DriverToken>` when a session exists.
 *
 * 🔴 The token is bound to `driver_id`, not to a user or a branch (CLAUDE.md
 * § المصادقة), which is what makes ONE login show the orders of every restaurant
 * the driver is linked to. So this interceptor deliberately sends no company or
 * branch header: there is no "current company" in a driver session, and inventing
 * one here would quietly scope away the other restaurants' work.
 *
 * Requests explicitly tagged [PublicEndpoint] are left unsigned — `app-version`,
 * `request-otp`, `verify-otp` and `login` are read or called BEFORE a token
 * exists. Tagging is used rather than URL matching so a renamed path cannot
 * silently start leaking a token, or silently stop sending one.
 */
class DriverAuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.tag(PublicEndpoint::class.java) != null) {
            return chain.proceed(request)
        }
        val token = tokenStore.token.value
        val signed = if (token.isNullOrBlank()) {
            request
        } else {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(signed)
    }
}

/** Marker tag: this call is made before/without a session. See [DriverAuthInterceptor]. */
class PublicEndpoint private constructor() {
    companion object {
        val INSTANCE = PublicEndpoint()
    }
}
