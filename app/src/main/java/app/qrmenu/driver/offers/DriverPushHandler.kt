package app.qrmenu.driver.offers

import android.content.Context
import android.content.Intent
import android.util.Log
import app.qrmenu.driver.MainActivity
import app.qrmenu.driver.R
import app.qrmenu.driver.alerts.OfferAlarm
import app.qrmenu.driver.alerts.OfferNotifier
import app.qrmenu.driver.alerts.offerPendingIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import app.qrmenu.driver.push.OfferPushPayload
import app.qrmenu.driver.push.PushHandler

/**
 * `:app`'s implementation of `:core:push`'s [PushHandler] — the seam that
 * lets that module post a real notification and open a real Activity
 * without ever depending on either.
 *
 * Bound to [PushHandler] via Hilt `@Binds` in [PushBindsModule].
 *
 * Everything here runs from [app.qrmenu.driver.push.DriverMessagingService]'s
 * FCM callback thread — a process that may have JUST been revived from
 * death for this one callback — so [onOfferPush] does the ring FIRST
 * (notification + alarm, both synchronous, both cheap) before anything
 * slower like a network call.
 */
@Singleton
class DriverPushHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offerGate: OfferGate,
    private val offerCoordinator: OfferCoordinator,
    private val alarm: OfferAlarm,
    private val notifier: OfferNotifier,
    private val deviceTokenRegistrar: DeviceTokenRegistrar,
) : PushHandler {

    // Not `viewModelScope` — this class has no ViewModel lifecycle, it lives
    // for the whole process, exactly like the arms it drives.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onOfferPush(payload: OfferPushPayload) {
        // Re-checked here even though DriverMessagingService already dropped
        // anything dead AT RECEIPT — that check and this one run at
        // different instants (there is real wall-clock time between "FCM
        // handed us the message" and "we are about to take over the
        // screen"), and a full-screen takeover for an offer that already
        // went to another driver is strictly worse than staying silent.
        if (!payload.isLive(Instant.now())) {
            Log.w(TAG, "not ringing: offer ${payload.offerId} for order ${payload.orderId} already expired")
            return
        }

        if (!offerGate.shouldRaise(payload.offerId)) {
            // Either the poll arm already raised this exact offer id, or an
            // earlier/duplicate push delivery got here first. Silent by
            // design — a second ring for the same offer is the bug this
            // gate exists to prevent, not a state worth logging as loud.
            Log.i(TAG, "not ringing: offer ${payload.offerId} already raised")
            return
        }

        Log.i(TAG, "ringing: offer ${payload.offerId} (wave ${payload.wave}) for order ${payload.orderId}")
        offerCoordinator.raise(payload.orderId, payload.offerId)
        runCatching { alarm.start() }.onFailure { Log.w(TAG, "alarm failed", it) }
        postNotification()
    }

    override fun onTokenRefreshed(token: String) {
        scope.launch { deviceTokenRegistrar.onTokenRefreshed(token) }
    }

    private fun postNotification() {
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // A single fixed request code, not one derived from the order id:
        // only one offer is ever pending at a time (decision 21, one trip at
        // a time), so there is never a second in-flight PendingIntent for
        // this notification to collide with.
        val contentIntent = offerPendingIntent(context, activityIntent, REQUEST_CODE)
        val fullScreenIntent = offerPendingIntent(context, activityIntent, REQUEST_CODE)

        notifier.notifyOffer(
            notificationId = OfferNotifier.OFFER_NOTIFICATION_ID,
            titleRes = R.string.offer_notification_title,
            bodyRes = R.string.offer_notification_body,
            contentIntent = contentIntent,
            fullScreenIntent = fullScreenIntent,
        )
    }

    private companion object {
        const val TAG = "DriverPush"
        const val REQUEST_CODE = 4501
    }
}
