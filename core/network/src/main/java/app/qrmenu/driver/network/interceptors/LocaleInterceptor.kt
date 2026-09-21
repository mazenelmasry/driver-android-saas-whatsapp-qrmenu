package app.qrmenu.driver.network.interceptors

import app.qrmenu.driver.datastore.AppLocaleStore
import java.util.Locale
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Sends `Accept: application/json` and `Accept-Language`.
 *
 * `Accept` is not cosmetic: without it Laravel renders an HTML error page for a
 * 401/422/500, and the JSON parser then fails with "Expected start of object
 * '{', but had '<'" instead of the real reason — hiding exactly the failures
 * that need reading.
 *
 * `Accept-Language` comes from the IN-APP language ([AppLocaleStore]), not from
 * `Locale.getDefault()`. The in-app picker is independent of the system language
 * by design (CLAUDE.md decision 9), and background work — the location
 * heartbeat, the offline action queue — can start the process with no Activity,
 * so `attachBaseContext` may never have run. [AppLocaleStore] is a singleton over
 * SharedPreferences and answers correctly in every process.
 *
 * ⚠️ The server only speaks ar/en. A driver reading Urdu/Bengali/Hindi therefore
 * gets an ar/en `message` — which is precisely why the app translates by `code`
 * and never displays that string.
 */
class LocaleInterceptor @Inject constructor(
    private val appLocaleStore: AppLocaleStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val language = appLocaleStore.language.value?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().language.takeIf { it.isNotBlank() }
            ?: FALLBACK_LANGUAGE
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("Accept-Language", language)
            .build()
        return chain.proceed(request)
    }

    private companion object {
        const val FALLBACK_LANGUAGE = "ar"
    }
}
