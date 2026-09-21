package app.qrmenu.driver.push

/**
 * The seam that keeps `:core:push` independent of `:app`.
 *
 * The offer notification needs a PendingIntent into an Activity that lives
 * in `:app`, so a dependency the other way round (this module reaching into
 * `:app`) would be a cycle. `:app` binds this interface (via Hilt `@Binds`)
 * to an implementation that knows how to build that intent and hand off to
 * `:core:notifications` — this module never references either.
 *
 * Deliberately NOT bound to a default no-op here: a silent default is
 * exactly how a push arm dies unnoticed, and satisfying the Hilt graph is
 * `:app`'s job, not this module's.
 */
interface PushHandler {

    /** A live (non-expired), well-formed offer push arrived. */
    fun onOfferPush(payload: OfferPushPayload)

    /** FCM issued a new registration token — needs re-sending to the backend. */
    fun onTokenRefreshed(token: String)
}
