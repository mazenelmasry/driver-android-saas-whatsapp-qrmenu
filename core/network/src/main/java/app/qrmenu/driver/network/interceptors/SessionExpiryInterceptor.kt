package app.qrmenu.driver.network.interceptors

import app.qrmenu.driver.datastore.TokenStore
import java.net.HttpURLConnection
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Ends the session the moment the server stops recognising it.
 *
 * 🔴 Without this, a revoked token turns every screen into "something went
 * wrong" with a Try again button that can never succeed — the driver taps it,
 * nothing happens, and the app looks broken when in fact it simply needs them
 * to sign in again. A token dies for ordinary reasons: it expires (30 days),
 * the restaurant suspends the driver, or the driver signs in on another phone.
 *
 * Clearing the store is enough to move the app: `MainActivity` watches
 * [TokenStore.token] and drops to the sign-in screen when a session that
 * existed goes away.
 *
 * Deliberately ignores 401 on a request that was never signed
 * ([PublicEndpoint]) — `login` answering "wrong password" with a 401 is an
 * answer, not an expiry, and clearing a session there would be meaningless.
 */
class SessionExpiryInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val wasSigned = request.tag(PublicEndpoint::class.java) == null &&
            request.header("Authorization") != null

        if (wasSigned && response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            tokenStore.clear()
        }

        return response
    }
}
