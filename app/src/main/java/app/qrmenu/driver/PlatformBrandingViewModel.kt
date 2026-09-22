package app.qrmenu.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.datastore.PlatformBranding
import app.qrmenu.driver.datastore.PlatformBrandingStore
import app.qrmenu.driver.database.NotificationHistoryStore
import app.qrmenu.driver.network.api.AuthApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Keeps the platform's identity — its name, logo and support links — fresh.
 *
 * 🔴 The app used to read `GET /driver/branding` on exactly ONE screen: sign
 * in. A driver sees that screen once and then never again, so everything after
 * it was anonymous, and a rebrand in the admin panel reached nobody who was
 * already signed in.
 *
 * The cached answer is served immediately and the refresh runs behind it, so
 * the header never waits on the network for something it already knows. A
 * failed refresh is deliberately silent: the last known identity is still
 * correct, and an error message about a logo would be noise on a screen where
 * the driver is waiting for work.
 */
@HiltViewModel
class PlatformBrandingViewModel @Inject constructor(
    private val store: PlatformBrandingStore,
    private val authApi: AuthApi,
    notificationHistory: NotificationHistoryStore,
) : ViewModel() {

    val branding: StateFlow<PlatformBranding> = store.branding

    /**
     * How many notifications the driver has not looked at, for the bell.
     *
     * Carried by this view model rather than a second one because it is the
     * same kind of value as the branding beside it: something the SHELL shows
     * on every tab, owned by nobody's screen. The store is a singleton, so
     * reading it here costs nothing and spares four call sites their own
     * plumbing.
     */
    val unreadNotifications: StateFlow<Int> = notificationHistory.unreadCount

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            runCatching { authApi.branding() }
                .onSuccess { dto ->
                    store.store(
                        PlatformBranding(
                            nameAr = dto.nameAr,
                            nameEn = dto.nameEn,
                            logoUrl = dto.logoUrl,
                            accentColor = dto.accentColor,
                            privacyUrl = dto.privacyUrl,
                            termsUrl = dto.termsUrl,
                            helpUrl = dto.helpUrl,
                            supportWhatsApp = dto.supportWhatsApp,
                            supportEmail = dto.supportEmail,
                        ),
                    )
                }
        }
    }
}
