package app.qrmenu.driver.network.interceptors

import app.qrmenu.driver.network.BuildConfig
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reports which build is talking, so `driver.client` middleware can refuse one
 * below `min_version_code` with 426 + `app_update_required`.
 *
 * 🔴 `X-App-Version` carries the integer versionCode, not the name. As a string
 * "1.10.0" sorts BEFORE "1.9.0", which would lock out the driver running the
 * newest build (CLAUDE.md § ترقيم الإصدارات). The name is sent separately, for
 * logs and crash reports only.
 */
class AppVersionInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-App-Version", BuildConfig.APP_VERSION_CODE.toString())
            .header("X-App-Version-Name", BuildConfig.APP_VERSION_NAME)
            .header("X-App-Platform", "android")
            .build()
        return chain.proceed(request)
    }
}
