package app.qrmenu.driver.location.upload

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the last `driver/location` upload reached the server.
 *
 * CLAUDE.md: "والتطبيق يعرض 'الاتصال منقطع' فور فشل الإرسال" — this is the
 * observable truth `:feature:availability`'s banner is built from. It starts
 * `true` (optimistic — the driver has not tried to send anything yet, so
 * "disconnected" would be a lie) and flips on the very first failed/succeeded
 * upload after that.
 *
 * This module does NOT build the banner — only exposes the fact.
 */
@Singleton
class LocationConnectivityState @Inject constructor() {

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastSuccessfulUploadAtMillis = MutableStateFlow<Long?>(null)
    val lastSuccessfulUploadAtMillis: StateFlow<Long?> = _lastSuccessfulUploadAtMillis.asStateFlow()

    fun markSendSucceeded(nowMillis: Long) {
        _isConnected.value = true
        _lastSuccessfulUploadAtMillis.value = nowMillis
    }

    fun markSendFailed() {
        _isConnected.value = false
    }
}
