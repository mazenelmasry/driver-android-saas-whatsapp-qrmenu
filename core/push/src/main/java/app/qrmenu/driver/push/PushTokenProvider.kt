package app.qrmenu.driver.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The only place that touches [FirebaseMessaging] directly — everything
 * else asks [PushHandler]. Safe to call on a brand with no Firebase project
 * (taaj ships without `google-services.json`): it never throws, it returns
 * null instead.
 *
 * The check is done at RUNTIME via [FirebaseApp.getApps] rather than by
 * reading `:app`'s `BuildConfig.FIREBASE_CONFIGURED` — this module cannot
 * see another module's generated BuildConfig, and the runtime check is the
 * truthful one anyway: it is true exactly when `FirebaseApp` actually
 * initialised, which is what determines whether [FirebaseMessaging] can be
 * used at all.
 */
@Singleton
class PushTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Null when Firebase never initialised, or the token fetch failed. */
    suspend fun currentToken(): String? {
        if (!isFirebaseAvailable()) {
            return null
        }
        return runCatching { awaitToken() }.getOrNull()
    }

    /** Null when Firebase never initialised, or the deletion failed. */
    suspend fun deleteToken() {
        if (!isFirebaseAvailable()) {
            return
        }
        runCatching { awaitDeleteToken() }
    }

    private fun isFirebaseAvailable(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    // kotlinx-coroutines-play-services is not a declared dependency in this
    // module — the Task→coroutine bridge is hand-rolled with
    // suspendCancellableCoroutine + addOnCompleteListener instead of adding one.
    private suspend fun awaitToken(): String = suspendCancellableCoroutine { continuation ->
        val task = FirebaseMessaging.getInstance().token
        task.addOnCompleteListener { completed ->
            val exception = completed.exception
            if (exception != null) {
                continuation.cancel(exception)
            } else {
                continuation.resumeWith(Result.success(completed.result))
            }
        }
    }

    private suspend fun awaitDeleteToken(): Unit = suspendCancellableCoroutine { continuation ->
        val task = FirebaseMessaging.getInstance().deleteToken()
        task.addOnCompleteListener { completed ->
            val exception = completed.exception
            if (exception != null) {
                continuation.cancel(exception)
            } else {
                continuation.resumeWith(Result.success(Unit))
            }
        }
    }
}
