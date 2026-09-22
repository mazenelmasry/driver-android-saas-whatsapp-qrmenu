package app.qrmenu.driver.network.di

import app.qrmenu.driver.network.BuildConfig
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.api.AvailabilityApi
import app.qrmenu.driver.network.api.LedgerApi
import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.interceptors.AppVersionInterceptor
import app.qrmenu.driver.network.interceptors.DnsRetryInterceptor
import app.qrmenu.driver.network.interceptors.DriverAuthInterceptor
import app.qrmenu.driver.network.interceptors.LocaleInterceptor
import app.qrmenu.driver.network.interceptors.SessionExpiryInterceptor
import app.qrmenu.driver.network.interceptors.UpdateRequiredInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideJson(): Json = Json {
        // A field this build has never heard of must not fail the decode: the
        // server ships ahead of the phone in every driver's pocket.
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        // 🔴 coerceInputValues is deliberately NOT enabled (the POS app has it
        // and paid for it): with it, an explicit `null` on a non-nullable field
        // that has a default is silently REWRITTEN to that default. On this
        // surface those fields are money — a null `cash_to_collect` would become
        // 0.00 and the driver would collect nothing. Every field the contract
        // marks nullable is nullable in the DTOs instead, which is checked by
        // BackendContractMirrorTest.
    }

    @Provides @Singleton
    fun provideOkHttp(
        dnsRetry: DnsRetryInterceptor,
        auth: DriverAuthInterceptor,
        locale: LocaleInterceptor,
        appVersion: AppVersionInterceptor,
        sessionExpiry: SessionExpiryInterceptor,
        updateRequired: UpdateRequiredInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        // A phone on mobile data: long enough to survive a lift or a tunnel,
        // short enough that an offer's 45s countdown is not spent waiting.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Outermost, so a retry re-runs the whole chain and re-attaches fresh
        // auth/locale headers exactly as a first attempt would.
        .addInterceptor(dnsRetry)
        .addInterceptor(auth)
        .addInterceptor(locale)
        .addInterceptor(appVersion)
        // After `auth`, so it can see whether the request it is judging was
        // actually signed — an unsigned 401 is an answer, not an expiry.
        .addInterceptor(sessionExpiry)
        // A 426 can arrive on any protected route regardless of session state,
        // so position relative to `sessionExpiry` does not matter — both only
        // read the response, neither short-circuits the other.
        .addInterceptor(updateRequired)
        .apply {
            if (BuildConfig.DEBUG) {
                // `php artisan serve` is single-threaded and garbles response
                // bodies when requests overlap — the location heartbeat and the
                // order poll corrupt each other's JSON. Serialise them in debug
                // only; production talks to FPM/nginx, where this never applies.
                dispatcher(
                    Dispatcher().apply {
                        maxRequests = 1
                        maxRequestsPerHost = 1
                    },
                )
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
                )
            }
        }
        .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // The host comes from the build flavour (taaj/meniura) and never from a
        // literal in the code. `/api/v1/` only — the driver routes live under
        // `driver/…`, which each endpoint states in full, and this app must
        // never be able to address a `/pos/*` route.
        .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides @Singleton
    fun provideAvailabilityApi(retrofit: Retrofit): AvailabilityApi =
        retrofit.create(AvailabilityApi::class.java)

    @Provides @Singleton
    fun provideOrderApi(retrofit: Retrofit): OrderApi = retrofit.create(OrderApi::class.java)

    @Provides @Singleton
    fun provideLedgerApi(retrofit: Retrofit): LedgerApi = retrofit.create(LedgerApi::class.java)
}
