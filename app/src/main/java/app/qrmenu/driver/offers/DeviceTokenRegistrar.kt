package app.qrmenu.driver.offers

import android.util.Log
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.DeviceTokenRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.qrmenu.driver.push.PushTokenProvider

/**
 * The one place that sends this phone's FCM registration token to the
 * backend — called from three different moments that all reduce to the
 * same question ("is there a live session, and does the server have this
 * phone's current token?"):
 *
 *  1. Right after sign-in.
 *  2. On every app start with a session that survived process death.
 *  3. Whenever FCM rotates the token ([DriverPushHandler.onTokenRefreshed]) —
 *     which can happen at any time, session or no session.
 *
 * A token that arrives from (3) with no session yet (FCM can rotate before
 * the driver has signed in on a fresh install) is remembered in
 * [pendingToken] rather than dropped, so the very next successful sign-in
 * registers it — otherwise the phone would silently miss offers until FCM
 * happened to rotate the token again.
 *
 * Registration failure is deliberately non-fatal everywhere it is called:
 * a driver must never be blocked from working because a push-registration
 * call failed. The poll arm covers the gap until the next attempt.
 */
@Singleton
class DeviceTokenRegistrar @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val pushTokenProvider: PushTokenProvider,
) {

    private val mutex = Mutex()
    private var pendingToken: String? = null

    /**
     * The token this PROCESS has already registered. Both call (1) and call
     * (2) fire on a normal cold start — `DriverApplication` at process start
     * and `MainActivity` when `signedIn` first turns true — which sent two
     * identical POSTs per launch (observed on the S25). The server treats a
     * repeat as a no-op, so nothing was wrong, but it is a wasted round trip
     * on a driver's mobile data every time they open the app.
     *
     * Deliberately NOT persisted: a fresh process re-registers once, which is
     * the cheap way to heal a token the server lost or never stored. Cleared
     * by [forgetLocalState] so the NEXT driver on this phone registers for
     * real instead of inheriting the previous driver's memo.
     */
    private var registeredToken: String? = null

    /**
     * Call (1) after sign-in, and (2) on every app start. Reads the CURRENT
     * token from FCM itself rather than trusting anything cached — on the
     * `taaj` brand (no Firebase project) [PushTokenProvider.currentToken]
     * returns null and this is a no-op, exactly as it should be.
     */
    suspend fun registerCurrentToken() {
        if (!tokenStore.hasValidSession()) return
        val token = pendingToken ?: pushTokenProvider.currentToken() ?: return
        register(token)
    }

    /** Call (3), from [DriverPushHandler.onTokenRefreshed]. */
    suspend fun onTokenRefreshed(token: String) {
        if (!tokenStore.hasValidSession()) {
            // Remembered, not dropped — see this class's own doc.
            pendingToken = token
            return
        }
        register(token)
    }

    /**
     * Local sign-out cleanup. The SERVER-side clearing of this phone's
     * token happens inside `driver/auth/logout` itself (backend-owned,
     * CLAUDE.md decision) — this only forgets a token this object might
     * still be holding for the driver who just signed out, so a second
     * driver signing in on this same phone registers cleanly rather than
     * silently reusing state left over from the first.
     */
    fun forgetLocalState() {
        pendingToken = null
        registeredToken = null
    }

    private suspend fun register(token: String) {
        mutex.withLock {
            if (token == registeredToken) return
            runCatching { authApi.registerDeviceToken(DeviceTokenRequest(token)) }
                .onSuccess { registeredToken = token }
                .onFailure { Log.w(TAG, "device token registration failed — will retry next app start", it) }
            pendingToken = null
        }
    }

    private companion object {
        const val TAG = "DriverPush"
    }
}
