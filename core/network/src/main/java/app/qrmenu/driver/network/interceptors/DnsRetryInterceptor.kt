package app.qrmenu.driver.network.interceptors

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries a request that never reached the server because the OS resolver
 * momentarily could not translate the hostname, or refused the first connect.
 *
 * This is the single most common failure on aggressively power-managed phones
 * (HONOR/Huawei, Xiaomi, Oppo, Vivo — the exact devices a delivery driver is
 * most likely to own): the OS freezes the app and tears down its network, so the
 * FIRST request after it is un-frozen — screen-on, or the location service
 * waking up — throws [UnknownHostException] even though the network is healthy,
 * and the identical lookup succeeds a fraction of a second later. Field-confirmed
 * on a HONOR device in the POS app.
 *
 * 🔴 SAFETY — why this cannot double-submit:
 * Only [UnknownHostException] and [ConnectException] are retried. Both are thrown
 * BEFORE a single byte leaves the device (DNS lookup / TCP connect), so the
 * server never saw the request and re-issuing it is not a repeat. A
 * `SocketTimeoutException` or a mid-stream [IOException] is deliberately NOT
 * retried: those can happen AFTER the body was sent, where a blind retry of
 * `delivered` would credit the ledger twice. (The trip commands also carry an
 * `Idempotency-Key`, but this interceptor stays conservative regardless of that.)
 *
 * Placed OUTERMOST so a retry re-runs the whole chain and re-attaches a fresh
 * Authorization/Accept-Language header exactly as a first attempt would.
 */
class DnsRetryInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var lastError: IOException? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                return chain.proceed(chain.request())
            } catch (e: UnknownHostException) {
                lastError = e
            } catch (e: ConnectException) {
                lastError = e
            }
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(BACKOFF_MS[attempt])
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw lastError
                }
            }
        }
        throw lastError ?: UnknownHostException("DNS retry exhausted")
    }

    private companion object {
        const val MAX_RETRIES = 3
        /** Short escalating waits: ≈1.1s worst case, invisible next to a 45s offer. */
        val BACKOFF_MS = longArrayOf(150L, 350L, 600L)
    }
}
