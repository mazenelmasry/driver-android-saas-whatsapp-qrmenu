package app.qrmenu.driver.health

import android.content.Intent
import app.qrmenu.driver.alerts.NotificationHealth
import app.qrmenu.driver.alerts.NotificationHealthConcern
import app.qrmenu.driver.alerts.NotificationHealthProvider
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin Compose-facing wrapper over [NotificationHealthProvider]: reads a
 * fresh [NotificationHealth] on demand ([refresh]) rather than continuously,
 * because the platform pushes no "a permission/channel/DND setting changed"
 * event — the only reliable moment to re-check is when the driver returns to
 * the app (see [NotificationHealthBanner]'s resume hook).
 */
@HiltViewModel
class NotificationHealthViewModel @Inject constructor(
    private val provider: NotificationHealthProvider,
) : ViewModel() {

    private val _health = MutableStateFlow(provider.current())
    val health: StateFlow<NotificationHealth> = _health.asStateFlow()

    fun refresh() {
        _health.value = provider.current()
    }

    fun settingsIntentFor(concern: NotificationHealthConcern): Intent =
        provider.settingsIntentFor(concern)
}
