package app.qrmenu.driver.health

/**
 * Whether the `POST_NOTIFICATIONS` dialog has already been shown once in
 * this process — the state [NotificationPermissionRequestGate] is gating on.
 *
 * A process-lifetime object rather than composition-scoped state: the
 * composable that requests it ([RequestNotificationPermissionOnce]) sits
 * inside `SignedInScreen`, which is torn down on sign-out and rebuilt on the
 * next sign-in within the SAME process. Anchoring "already asked" to that
 * composable's own lifetime would let a sign-out/sign-in cycle re-ask; this
 * object outlives it.
 */
object NotificationPermissionAskOnce {

    @Volatile
    private var asked = false

    fun hasAsked(): Boolean = asked

    fun markAsked() {
        asked = true
    }
}
