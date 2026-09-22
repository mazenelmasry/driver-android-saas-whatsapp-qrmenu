package app.qrmenu.driver.updater

import app.qrmenu.driver.network.dto.AppVersionDto

/**
 * The one place that decides, from the two signals the app ever gets about
 * its own staleness, what the driver should see.
 *
 * The two signals are independent and must be combined, not chosen between:
 * - [AppVersionDto], from the advisory poll (`driver/app-version`, launch +
 *   every 6h) — a floor the backend is WILLING to serve, checked lazily.
 * - `interceptorMinVersionCode`, from [app.qrmenu.driver.network.update.ClientUpdateGate] —
 *   the floor the backend just PROVED it enforces, because some protected
 *   route already answered 426 this process. It can be known (a real
 *   `min_version_code`) or unknown (`Int.MAX_VALUE`, see the gate's own doc)
 *   but never "not raised" once a 426 has landed, and it never clears itself.
 *
 * Taking the max of the two means a driver who is blocked by a live 426 stays
 * blocked even if the next advisory poll (a DIFFERENT endpoint, `app-version`
 * itself is never gated) happens to report a lower, stale floor — the gate
 * has stronger evidence than the poll and must never be overridden by it.
 *
 * 🔴 The comparison is on `versionCode`, always. Never compare `versionName`
 * strings — see [app.qrmenu.driver.DriverVersion]'s own doc for why that is
 * frozen: "1.10.0" sorts before "1.9.0" as text.
 */
object UpdateDecision {

    /**
     * Whether the current build may keep running at all.
     *
     * [hasActiveTrip] is a TRI-STATE, not a plain `Boolean` — this is the one
     * correction a real device caught that no amount of reasoning about the
     * OTHER two states would have surfaced. `SignedInScreen` cannot answer
     * "is a trip in this driver's hands?" from memory alone: on a cold start
     * (process killed, phone rebooted mid-shift, or the floor raised while
     * the app was backgrounded) NOTHING in the UI has loaded yet, so the only
     * honest values are `true` (a trip is confirmed held — resolved either
     * from `driver/orders/mine` or from a route already open to one),
     * `false` (that same lookup came back and confirmed there is none), or
     * **`null` — the lookup has not answered yet, or it failed** (offline).
     *
     * A caller that could not tell the difference between "confirmed none"
     * and "don't know" and defaulted the ambiguous case to `false` is exactly
     * how this bug shipped: `activeTripId` was `null` at the instant of a
     * cold start for the SAME reason a genuinely tripless driver's `null` is
     * — the app hadn't asked yet — and both were read as "no trip, block".
     * A driver actually mid-delivery was walled off with no way for the
     * screen that would have proven otherwise to ever load.
     *
     * 🔴 `null` (unknown) is treated exactly like `true` (confirmed held) —
     * [UpdateRequirement.DeferredForActiveTrip], never [UpdateRequirement.Blocked]
     * — the same "unanswerable means allowed" doctrine the rest of this
     * system already follows for a failed poll (see [AppVersionRepository.check]).
     * Blocking a driver who MIGHT be holding a customer's order over one who
     * definitely isn't is the wrong trade in both directions: a late wall
     * costs the platform an update cycle; an early one stops a delivery in
     * progress. `null` gets its own [UpdateRequirement.DeferredUnknown] rather
     * than being folded into [UpdateRequirement.DeferredForActiveTrip] because
     * the UI text is different — "finish your trip first" is a lie to a
     * driver who does not have one, and there being nothing to caption while
     * genuinely unknown is the honest screen.
     *
     * The deferral needs no state of its own to be "once": it is simply true
     * for exactly as long as [hasActiveTrip] is non-`false`, and the moment
     * it resolves to `false` (this trip ended, or the lookup came back and
     * found none) the SAME floor breach (nothing else changed) evaluates to
     * [UpdateRequirement.Blocked] on the very next read — there is no future
     * trip this deferral could accidentally cover, because acquiring a new
     * one requires the availability screen, which sits behind the block.
     */
    fun requirement(
        currentVersionCode: Int,
        remote: AppVersionDto?,
        interceptorMinVersionCode: Int?,
        hasActiveTrip: Boolean?,
    ): UpdateRequirement {
        val remoteMin = remote?.minVersionCode?.takeIf { it > 0 } ?: 0
        val gateMin = interceptorMinVersionCode?.takeIf { it > 0 } ?: 0
        val effectiveMin = maxOf(remoteMin, gateMin)

        if (effectiveMin <= 0 || currentVersionCode >= effectiveMin) {
            return UpdateRequirement.NotRequired
        }

        val info = UpdateInfo(
            versionName = remote?.minVersionName,
            downloadUrl = remote?.downloadUrl,
            notes = remote?.notes,
        )

        return when (hasActiveTrip) {
            true -> UpdateRequirement.DeferredForActiveTrip(info)
            null -> UpdateRequirement.DeferredUnknown(info)
            false -> UpdateRequirement.Blocked(info)
        }
    }

    /**
     * Whether the dismissible "an update exists" banner should show.
     *
     * Independent of [requirement] on purpose: a build that is already
     * BLOCKED has nothing to gain from also carrying a banner underneath a
     * screen the driver cannot reach — the caller only asks this when
     * [requirement] is [UpdateRequirement.NotRequired].
     *
     * [dismissedForVersionCode] is "the latest_version_code the driver already
     * dismissed the banner for" — re-shown only once a NEWER `latest_version_code`
     * ships, never again for the one they already saw and closed.
     */
    fun availability(
        currentVersionCode: Int,
        remote: AppVersionDto?,
        dismissedForVersionCode: Int?,
    ): UpdateAvailability {
        val latest = remote?.latestVersionCode?.takeIf { it > 0 } ?: return UpdateAvailability.UpToDate
        if (currentVersionCode >= latest) return UpdateAvailability.UpToDate
        if (dismissedForVersionCode == latest) return UpdateAvailability.UpToDate

        return UpdateAvailability.Optional(
            latestVersionCode = latest,
            info = UpdateInfo(
                versionName = remote.latestVersionName,
                downloadUrl = remote.downloadUrl,
                notes = remote.notes,
            ),
        )
    }
}

/** The display-only fields a version floor carries — never compared, only shown. */
data class UpdateInfo(
    val versionName: String?,
    val downloadUrl: String?,
    val notes: String?,
)

sealed interface UpdateRequirement {
    data object NotRequired : UpdateRequirement

    /** Below the floor, but a trip is in the driver's hands — allowed to finish it. */
    data class DeferredForActiveTrip(val info: UpdateInfo) : UpdateRequirement

    /**
     * Below the floor, and it is not yet known whether a trip is held —
     * see [UpdateDecision.requirement]'s doc on why this is not [Blocked].
     * Renders as nothing (no wall, no banner): there is no trip screen to
     * caption and no confirmed absence to act on, only a lookup in flight.
     */
    data class DeferredUnknown(val info: UpdateInfo) : UpdateRequirement

    /** Below the floor and CONFIRMED free to act — the full-screen blocker. */
    data class Blocked(val info: UpdateInfo) : UpdateRequirement
}

sealed interface UpdateAvailability {
    data object UpToDate : UpdateAvailability
    data class Optional(val latestVersionCode: Int, val info: UpdateInfo) : UpdateAvailability
}
