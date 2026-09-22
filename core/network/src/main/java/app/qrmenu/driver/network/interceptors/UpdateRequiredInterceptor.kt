package app.qrmenu.driver.network.interceptors

import app.qrmenu.driver.network.update.ClientUpdateGate
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response

/** Server refuses builds below its configured floor with this HTTP status (CLAUDE.md § أكواد الأخطاء). */
private const val HTTP_UPGRADE_REQUIRED = 426

/**
 * Raises [ClientUpdateGate] the moment the backend answers 426
 * `app_update_required` — the one signal that this specific build, not just
 * "something", is what is broken.
 *
 * Modelled on [SessionExpiryInterceptor]: it only observes and records, it
 * never navigates — screens react to [ClientUpdateGate] themselves, exactly as
 * `MainActivity` reacts to [app.qrmenu.driver.datastore.TokenStore.token].
 *
 * 🔴 Unlike [SessionExpiryInterceptor] this does NOT clear [ClientUpdateGate]
 * on success, and does NOT touch the auth token. An out-of-date build is not
 * an invalid session — clearing the token would drop the driver onto a
 * sign-in screen where entering the same credentials again cannot possibly
 * satisfy a version floor, only refreshing the app can. See [ClientUpdateGate]
 * for why the gate itself is also never cleared here.
 *
 * Body access uses [Response.peekBody]: on a 426 this is the ONLY interceptor
 * whose job is the body, but it sits ahead of callers (Retrofit, error
 * classification) that still need to read the real, un-consumed body stream.
 */
class UpdateRequiredInterceptor @Inject constructor(
    private val gate: ClientUpdateGate,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == HTTP_UPGRADE_REQUIRED) {
            gate.raise(minVersionCodeFrom(response))
        }

        return response
    }

    private fun minVersionCodeFrom(response: Response): Int? = runCatching {
        // A generously small cap: this body is a short JSON error envelope,
        // never a payload large enough to need streaming.
        val body = response.peekBody(MAX_PEEK_BYTES).string()
        json.parseToJsonElement(body).jsonObject["min_version_code"]
            ?.jsonPrimitive
            ?.content
            ?.toIntOrNull()
    }.getOrNull()

    private companion object {
        const val MAX_PEEK_BYTES = 8L * 1024
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
