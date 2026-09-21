package app.qrmenu.driver.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.qrmenu.driver.common.session.TokenExpiry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The driver's session, at rest in `EncryptedSharedPreferences` (AES-256, master
 * key in the Android Keystore) — mirrors the POS app's `TokenStore` for the same
 * reasons: one dependency, no serialisation, and it fits a single-session client
 * exactly.
 *
 * ## Why so much less is stored here than in the POS store
 * The POS token is bound to ONE branch of ONE company, so its store caches the
 * branch, the company and the receipt template to survive a tablet that boots
 * offline at the start of a shift. A driver token is bound to **`driver_id`, not
 * `user_id`** (CLAUDE.md § auth), and a driver may work for several restaurants
 * at once — so "which company am I in" is not a property of the SESSION at all.
 * It belongs to the order being worked and to the restaurant switcher, and is
 * fetched with them. Caching a "current company" here would invent a piece of
 * state the model does not have, and the ledger — which is per (driver ×
 * company) and never nets across restaurants — is exactly where that invention
 * would do damage.
 *
 * [driverId] IS kept, because the private Reverb channel is
 * `private-driver.{driverId}` and the app has to subscribe before any screen
 * has loaded data.
 *
 * ## One device session
 * Issuing a token server-side revokes the previous one (CLAUDE.md § auth), so
 * there is never more than one row to hold. [deviceName] is what the driver
 * typed at login and is echoed back by `GET /driver/me`; it is stored so the
 * settings screen can show which device holds the session without a round-trip.
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, null))
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _driverId = MutableStateFlow(prefs.getLong(KEY_DRIVER_ID, -1L).takeIf { it != -1L })

    /** Needed to subscribe to `private-driver.{id}` before any screen has data. */
    val driverId: StateFlow<Long?> = _driverId.asStateFlow()

    private val _driverName = MutableStateFlow(prefs.getString(KEY_DRIVER_NAME, null))
    val driverName: StateFlow<String?> = _driverName.asStateFlow()

    private val _phone = MutableStateFlow(prefs.getString(KEY_PHONE, null))

    /** E.164, as canonicalised by the server — pre-fills the login screen after a logout. */
    val phone: StateFlow<String?> = _phone.asStateFlow()

    private val _deviceName = MutableStateFlow(prefs.getString(KEY_DEVICE_NAME, null))
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _expiresAt = MutableStateFlow(prefs.getLong(KEY_EXPIRES_AT, -1L).takeIf { it != -1L })
    val expiresAt: StateFlow<Long?> = _expiresAt.asStateFlow()

    /**
     * Whether there is a usable session right now.
     *
     * Deliberately a FUNCTION taking the clock, not a stored flag and not a
     * `val`: a token that was alive when the app was last opened may be dead
     * now, and a boolean written at login would keep saying "fine" for 30 days.
     * The rule itself lives in [TokenExpiry] where it is unit-tested.
     */
    fun hasValidSession(nowMillis: Long = System.currentTimeMillis()): Boolean =
        _token.value != null && !TokenExpiry.isExpired(_expiresAt.value, nowMillis)

    /** True while the session still works but should be refreshed on the next call. */
    fun needsRenewal(nowMillis: Long = System.currentTimeMillis()): Boolean =
        _token.value != null && TokenExpiry.needsRenewal(_expiresAt.value, nowMillis)

    fun save(
        token: String,
        driverId: Long,
        driverName: String?,
        phone: String?,
        deviceName: String?,
        expiresAtMillis: Long?,
    ) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putLong(KEY_DRIVER_ID, driverId)
            putString(KEY_DRIVER_NAME, driverName)
            putString(KEY_PHONE, phone)
            putString(KEY_DEVICE_NAME, deviceName)
            if (expiresAtMillis != null) {
                putLong(KEY_EXPIRES_AT, expiresAtMillis)
            } else {
                remove(KEY_EXPIRES_AT)
            }
        }.apply()

        _token.value = token
        _driverId.value = driverId
        _driverName.value = driverName
        _phone.value = phone
        _deviceName.value = deviceName
        _expiresAt.value = expiresAtMillis
    }

    /**
     * Refreshes only the deadline, for the "renews on use" half of the rule —
     * without touching the token or the identity beside it.
     */
    fun touchExpiry(expiresAtMillis: Long) {
        prefs.edit().putLong(KEY_EXPIRES_AT, expiresAtMillis).apply()
        _expiresAt.value = expiresAtMillis
    }

    /**
     * Forgets the session on logout or on a 401.
     *
     * [phone] is kept on purpose: it is not a credential, and a driver who was
     * signed out mid-shift should not have to type their number again to get
     * back in — the PIN is the secret, not the phone number.
     */
    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_DRIVER_ID)
            .remove(KEY_DRIVER_NAME)
            .remove(KEY_DEVICE_NAME)
            .remove(KEY_EXPIRES_AT)
            .apply()

        _token.value = null
        _driverId.value = null
        _driverName.value = null
        _deviceName.value = null
        _expiresAt.value = null
    }

    /** Wipes everything, phone included — for "switch account" and for tests. */
    fun clearAll() {
        prefs.edit().clear().apply()
        _token.value = null
        _driverId.value = null
        _driverName.value = null
        _phone.value = null
        _deviceName.value = null
        _expiresAt.value = null
    }

    /**
     * A keystore that has been invalidated (a restored backup, a changed screen
     * lock on some OEM builds) makes every read here throw. Deleting the file
     * and starting again costs the driver one login; letting it throw costs them
     * an app that cannot be opened at all. Same recovery as the POS store.
     */
    private fun createEncryptedPrefs(context: Context): SharedPreferences =
        runCatching { buildEncryptedPrefs(context) }
            .getOrElse {
                context.deleteSharedPreferences(FILE_NAME)
                buildEncryptedPrefs(context)
            }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private companion object {
        const val FILE_NAME = "driver_session"
        const val KEY_TOKEN = "token"
        const val KEY_DRIVER_ID = "driver_id"
        const val KEY_DRIVER_NAME = "driver_name"
        const val KEY_PHONE = "phone"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
