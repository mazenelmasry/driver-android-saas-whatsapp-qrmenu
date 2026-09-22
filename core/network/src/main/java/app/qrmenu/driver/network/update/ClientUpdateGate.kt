package app.qrmenu.driver.network.update

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place the app learns that the SERVER, not just a version-check
 * screen, has refused to talk to this build.
 *
 * `driver/app-version` (polled on launch) is advisory — a driver can dismiss
 * it and keep using an old build for weeks. This gate is different: it is set
 * by [UpdateRequiredInterceptor] the moment ANY protected route answers 426
 * `app_update_required`, which the backend only sends once it has actually
 * stopped serving builds below its configured floor. Without a global signal
 * here, that refusal would surface as whatever screen happened to make the
 * request failing with a generic error — a driver mid-delivery would see
 * "something went wrong" on `delivered` with no way to know an update, not a
 * retry, is what fixes it.
 *
 * [requiredVersionCode] carries the `min_version_code` the 426 body reported,
 * so the screen that reacts to this gate can decide whether the driver's own
 * `versionCode` (known only to `:app`, which this module cannot depend on)
 * is already sufficient — the interceptor sees only the HTTP status, never
 * the phone's own build number.
 *
 * Deliberately never cleared automatically. A 426 means the backend's
 * configured floor is now above this build's version — that fact does not
 * stop being true because some OTHER request later returns 200 (a public,
 * untagged endpoint such as `app-version` itself, or a request that reached
 * the server before the floor was raised and is still in flight). The only
 * thing that can make the floor claim false again is the driver installing a
 * newer build and restarting the app, which re-creates this singleton with a
 * fresh, unset value — so "never clear" and "restart to clear" are the same
 * behaviour observed from two different angles.
 */
@Singleton
class ClientUpdateGate @Inject constructor() {

    private val _requiredVersionCode = MutableStateFlow<Int?>(null)

    /** `null` while no 426 has been seen this process; otherwise the reported `min_version_code`. */
    val requiredVersionCode: StateFlow<Int?> = _requiredVersionCode.asStateFlow()

    /** Called only by [UpdateRequiredInterceptor]. */
    internal fun raise(minVersionCode: Int?) {
        _requiredVersionCode.value = minVersionCode ?: NO_VERSION_REPORTED
    }

    private companion object {
        /**
         * The backend is contracted to always send `min_version_code` on a 426,
         * but a malformed or truncated body must still block the app — a gate
         * that silently stays closed because one optional-looking field failed
         * to parse is worse than one that blocks with an unknown floor.
         */
        const val NO_VERSION_REPORTED = Int.MAX_VALUE
    }
}
