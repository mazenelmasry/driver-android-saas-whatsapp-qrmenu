package app.qrmenu.driver.offers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The thin composable-facing wrapper around [DeviceTokenRegistrar], scoped
 * to the Activity (created once in `DriverApp`, before the sign-in gate, so
 * it survives across the signed-out ↔ signed-in transition rather than
 * being recreated by it).
 *
 * [DriverApplication] already attempts registration once at process start
 * (covers a session that survived process death with no Activity yet); this
 * covers the two moments that only exist once an Activity is running:
 * right after a fresh sign-in, and local cleanup right after sign-out.
 */
@HiltViewModel
class DeviceTokenViewModel @Inject constructor(
    private val registrar: DeviceTokenRegistrar,
) : ViewModel() {

    /** Call when `signedIn` flips to true — covers both a fresh sign-in and app start with an existing session. */
    fun registerAfterSignIn() {
        viewModelScope.launch { registrar.registerCurrentToken() }
    }

    /**
     * Call when `signedIn` flips to false. The SERVER-side token clearing
     * happens inside `driver/auth/logout` itself (backend-owned) — this is
     * only the local half, see [DeviceTokenRegistrar.forgetLocalState].
     */
    fun forgetAfterSignOut() {
        registrar.forgetLocalState()
    }
}
