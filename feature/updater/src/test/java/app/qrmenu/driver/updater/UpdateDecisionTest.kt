package app.qrmenu.driver.updater

import app.qrmenu.driver.network.dto.AppVersionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDecisionTest {

    // region requirement()

    @Test
    fun `below minimum blocks when no trip is in hand`() {
        val remote = AppVersionDto(minVersionCode = 10_200, latestVersionCode = 10_300)

        val result = UpdateDecision.requirement(
            currentVersionCode = 10_100,
            remote = remote,
            interceptorMinVersionCode = null,
            hasActiveTrip = false,
        )

        assertTrue(result is UpdateRequirement.Blocked)
    }

    @Test
    fun `below minimum defers instead of blocking while a trip is in hand`() {
        val remote = AppVersionDto(minVersionCode = 10_200, latestVersionCode = 10_300)

        val result = UpdateDecision.requirement(
            currentVersionCode = 10_100,
            remote = remote,
            interceptorMinVersionCode = null,
            hasActiveTrip = true,
        )

        assertTrue(result is UpdateRequirement.DeferredForActiveTrip)
    }

    @Test
    fun `the deferral ends the instant the trip does, on the very next read`() {
        val remote = AppVersionDto(minVersionCode = 10_200, latestVersionCode = 10_300)

        val whileHoldingATrip = UpdateDecision.requirement(10_100, remote, null, hasActiveTrip = true)
        val theMomentItEnds = UpdateDecision.requirement(10_100, remote, null, hasActiveTrip = false)

        assertTrue(whileHoldingATrip is UpdateRequirement.DeferredForActiveTrip)
        assertTrue(theMomentItEnds is UpdateRequirement.Blocked)
    }

    @Test
    fun `below minimum with an UNKNOWN trip status defers — never blocks on an unanswered question`() {
        // The device bug this guards against: a cold start where nothing has
        // asked `driver/orders/mine` yet must not read as "no trip, block" —
        // it must read as "don't know yet, don't block" (constraint 1,
        // "unanswerable means allowed").
        val remote = AppVersionDto(minVersionCode = 10_200, latestVersionCode = 10_300)

        val result = UpdateDecision.requirement(
            currentVersionCode = 10_100,
            remote = remote,
            interceptorMinVersionCode = null,
            hasActiveTrip = null,
        )

        assertTrue(result is UpdateRequirement.DeferredUnknown)
    }

    @Test
    fun `unknown resolving to a confirmed absence is what finally allows the block`() {
        val remote = AppVersionDto(minVersionCode = 10_200, latestVersionCode = 10_300)

        val whileUnanswered = UpdateDecision.requirement(10_100, remote, null, hasActiveTrip = null)
        val onceConfirmedNone = UpdateDecision.requirement(10_100, remote, null, hasActiveTrip = false)
        val onceConfirmedHeld = UpdateDecision.requirement(10_100, remote, null, hasActiveTrip = true)

        assertTrue(whileUnanswered is UpdateRequirement.DeferredUnknown)
        assertTrue(onceConfirmedNone is UpdateRequirement.Blocked)
        assertTrue(onceConfirmedHeld is UpdateRequirement.DeferredForActiveTrip)
    }

    @Test
    fun `between minimum and latest is not required — the caller's job is the banner`() {
        val remote = AppVersionDto(minVersionCode = 10_000, latestVersionCode = 10_300)

        val result = UpdateDecision.requirement(
            currentVersionCode = 10_100,
            remote = remote,
            interceptorMinVersionCode = null,
            hasActiveTrip = false,
        )

        assertEquals(UpdateRequirement.NotRequired, result)
    }

    @Test
    fun `at or above the minimum is never required`() {
        val remote = AppVersionDto(minVersionCode = 10_100, latestVersionCode = 10_300)

        val atFloor = UpdateDecision.requirement(10_100, remote, null, hasActiveTrip = false)
        val aboveFloor = UpdateDecision.requirement(10_200, remote, null, hasActiveTrip = false)

        assertEquals(UpdateRequirement.NotRequired, atFloor)
        assertEquals(UpdateRequirement.NotRequired, aboveFloor)
    }

    @Test
    fun `zero over zero — no poll yet, no gate raised — requires nothing`() {
        val result = UpdateDecision.requirement(
            currentVersionCode = 10_000,
            remote = null,
            interceptorMinVersionCode = null,
            hasActiveTrip = false,
        )

        assertEquals(UpdateRequirement.NotRequired, result)
    }

    @Test
    fun `a floor reported as exactly zero also requires nothing`() {
        val remote = AppVersionDto(minVersionCode = 0, latestVersionCode = 0)

        val result = UpdateDecision.requirement(10_000, remote, interceptorMinVersionCode = 0, hasActiveTrip = false)

        assertEquals(UpdateRequirement.NotRequired, result)
    }

    @Test
    fun `versionCode is compared as an integer, never as the versionName string`() {
        // 1.9.0 -> 10900, 1.10.0 -> 11000. As TEXT, "1.10.0" < "1.9.0" — the
        // exact trap CLAUDE.md's frozen versioning rule calls out. The driver
        // is already on the newer 1.10.0 build; the correct decision, from
        // the CODE alone, is NotRequired.
        val remote = AppVersionDto(minVersionCode = 10_900, latestVersionCode = 10_900, minVersionName = "1.9.0")

        val result = UpdateDecision.requirement(
            currentVersionCode = 11_000, // 1.10.0
            remote = remote,
            interceptorMinVersionCode = null,
            hasActiveTrip = false,
        )

        assertEquals(UpdateRequirement.NotRequired, result)
    }

    @Test
    fun `an unknown floor from a live 426 still blocks — never fails open`() {
        // ClientUpdateGate.NO_VERSION_REPORTED — a malformed/truncated 426
        // body must not silently leave the gate closed-but-inert.
        val result = UpdateDecision.requirement(
            currentVersionCode = 10_000,
            remote = null,
            interceptorMinVersionCode = Int.MAX_VALUE,
            hasActiveTrip = false,
        )

        assertTrue(result is UpdateRequirement.Blocked)
    }

    @Test
    fun `the interceptor's proven floor outranks a lower, stale advisory poll`() {
        val staleRemote = AppVersionDto(minVersionCode = 10_000, latestVersionCode = 10_000)

        val result = UpdateDecision.requirement(
            currentVersionCode = 10_050,
            remote = staleRemote,
            interceptorMinVersionCode = 10_200, // a live 426 proved a higher floor
            hasActiveTrip = false,
        )

        assertTrue(result is UpdateRequirement.Blocked)
    }

    // endregion

    // region availability()

    @Test
    fun `at or above the latest shows no banner`() {
        val remote = AppVersionDto(minVersionCode = 0, latestVersionCode = 10_200)

        val atLatest = UpdateDecision.availability(10_200, remote, dismissedForVersionCode = null)
        val aboveLatest = UpdateDecision.availability(10_300, remote, dismissedForVersionCode = null)

        assertEquals(UpdateAvailability.UpToDate, atLatest)
        assertEquals(UpdateAvailability.UpToDate, aboveLatest)
    }

    @Test
    fun `below latest offers the optional banner`() {
        val remote = AppVersionDto(minVersionCode = 0, latestVersionCode = 10_200)

        val result = UpdateDecision.availability(10_100, remote, dismissedForVersionCode = null)

        assertTrue(result is UpdateAvailability.Optional)
        assertEquals(10_200, (result as UpdateAvailability.Optional).latestVersionCode)
    }

    @Test
    fun `dismissing the banner for this exact latest version hides it`() {
        val remote = AppVersionDto(minVersionCode = 0, latestVersionCode = 10_200)

        val result = UpdateDecision.availability(10_100, remote, dismissedForVersionCode = 10_200)

        assertEquals(UpdateAvailability.UpToDate, result)
    }

    @Test
    fun `a newer release after a dismissal brings the banner back`() {
        val remote = AppVersionDto(minVersionCode = 0, latestVersionCode = 10_300)

        // Dismissed for 10_200 — but the poll has since seen 10_300 ship.
        val result = UpdateDecision.availability(10_100, remote, dismissedForVersionCode = 10_200)

        assertTrue(result is UpdateAvailability.Optional)
        assertEquals(10_300, (result as UpdateAvailability.Optional).latestVersionCode)
    }

    // endregion
}
