package app.qrmenu.driver.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

/**
 * The FCM entry point. This can run when the app process was DEAD — FCM
 * wakes it just for this callback — so `onMessageReceived` does no network
 * or disk I/O before deciding whether to ring: the payload already carries
 * everything needed (decision 13's data-only, high-priority message), and
 * FCM only gives a short window to act in.
 *
 * Declared in this module's manifest (merges into the app manifest) with no
 * default notification icon/channel meta-data — the messages are data-only
 * on purpose, and letting FCM build its own notification would fight
 * [PushHandler]'s full-screen-vs-heads-up decision in `:core:notifications`.
 */
@AndroidEntryPoint
class DriverMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushHandler: PushHandler

    @Inject
    lateinit var notificationHistoryRecorder: NotificationHistoryRecorder

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = OfferPushPayload.from(message.data)
        if (payload == null) {
            // Logged with the reason, not just "dropped" — an unexplained
            // silent drop is unanalysable when a driver reports a lost
            // offer, and this is the one place that would ever see it.
            Log.w(TAG, "dropping push: not a well-formed driver_offer (data=${message.data})")
            return
        }

        if (!payload.isLive(Instant.now())) {
            Log.w(TAG, "dropping push: offer ${payload.offerId} already expired at ${payload.expiresAt}")
            return
        }

        // Fire-and-forget (see NotificationHistoryRecorder's own doc) —
        // called before the ring so history reflects every live push that
        // reached this device even if something below throws, but it never
        // blocks or delays the ring itself: it launches on its own
        // background scope and returns immediately.
        notificationHistoryRecorder.recordOfferPush(payload)

        pushHandler.onOfferPush(payload)
    }

    override fun onNewToken(token: String) {
        pushHandler.onTokenRefreshed(token)
    }

    private companion object {
        const val TAG = "DriverPush"
    }
}
