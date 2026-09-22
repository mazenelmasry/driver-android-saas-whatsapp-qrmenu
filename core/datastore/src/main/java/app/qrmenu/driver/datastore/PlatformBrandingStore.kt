package app.qrmenu.driver.datastore

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The platform's own name, logo and support links, remembered between runs.
 *
 * 🔴 Why this is cached on the phone rather than simply fetched.
 *
 * `GET /driver/branding` is public and cheap, but the app is used on a moving
 * scooter — the connection is worst at exactly the moments a driver opens it.
 * The header carries the platform's identity on every screen, and an identity
 * that appears a second late, or not at all on a dead connection, is worse
 * than no identity: it reads as the app failing to load. So the last known
 * answer is written to [SharedPreferences] and served on the first frame,
 * while a refresh runs behind it.
 *
 * Plain [SharedPreferences] for the same reason [AppLocaleStore] and
 * [UiScaleStore] use them: this is read during the very first composition, and
 * a value that arrives one frame later is a visible flicker on every launch.
 *
 * Everything is nullable. A deployment that has configured none of this must
 * still give the driver a working app — the flavour's own name and the drawn
 * monogram are the fallback, and the links simply do not appear.
 */
@Singleton
class PlatformBrandingStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _branding = MutableStateFlow(read())

    val branding: StateFlow<PlatformBranding> = _branding.asStateFlow()

    /** Synchronous read for the very first composition. */
    fun current(): PlatformBranding = _branding.value

    /**
     * Stores a fresh answer from the server.
     *
     * A field the server left null OVERWRITES a cached one on purpose: an
     * admin who cleared the logo means the logo is gone, and a cache that
     * kept serving it would make that change impossible to make.
     */
    fun store(branding: PlatformBranding) {
        prefs.edit()
            .putString(KEY_NAME_AR, branding.nameAr)
            .putString(KEY_NAME_EN, branding.nameEn)
            .putString(KEY_LOGO_URL, branding.logoUrl)
            .putString(KEY_ACCENT_COLOR, branding.accentColor)
            .putString(KEY_PRIVACY_URL, branding.privacyUrl)
            .putString(KEY_TERMS_URL, branding.termsUrl)
            .putString(KEY_HELP_URL, branding.helpUrl)
            .putString(KEY_SUPPORT_WHATSAPP, branding.supportWhatsApp)
            .putString(KEY_SUPPORT_EMAIL, branding.supportEmail)
            .apply()
        _branding.value = branding
    }

    private fun read() = PlatformBranding(
        nameAr = prefs.getString(KEY_NAME_AR, null),
        nameEn = prefs.getString(KEY_NAME_EN, null),
        logoUrl = prefs.getString(KEY_LOGO_URL, null),
        accentColor = prefs.getString(KEY_ACCENT_COLOR, null),
        privacyUrl = prefs.getString(KEY_PRIVACY_URL, null),
        termsUrl = prefs.getString(KEY_TERMS_URL, null),
        helpUrl = prefs.getString(KEY_HELP_URL, null),
        supportWhatsApp = prefs.getString(KEY_SUPPORT_WHATSAPP, null),
        supportEmail = prefs.getString(KEY_SUPPORT_EMAIL, null),
    )

    private companion object {
        const val FILE_NAME = "driver_platform_branding"
        const val KEY_NAME_AR = "name_ar"
        const val KEY_NAME_EN = "name_en"
        const val KEY_LOGO_URL = "logo_url"
        const val KEY_ACCENT_COLOR = "accent_color"
        const val KEY_PRIVACY_URL = "privacy_url"
        const val KEY_TERMS_URL = "terms_url"
        const val KEY_HELP_URL = "help_url"
        const val KEY_SUPPORT_WHATSAPP = "support_whatsapp"
        const val KEY_SUPPORT_EMAIL = "support_email"
    }
}

/**
 * The platform's identity and its links, as last known.
 *
 * Deliberately a plain data class in `:core:datastore` rather than the network
 * DTO: the screens that read it must not depend on the shape of a response,
 * and a cached value has no business carrying a serializer.
 */
data class PlatformBranding(
    val nameAr: String? = null,
    val nameEn: String? = null,
    val logoUrl: String? = null,
    /**
     * The platform's accent, `#RRGGBB`, set in the admin panel.
     *
     * 🔴 This is what lets the two platforms look like two platforms. Both
     * flavours currently ship the SAME palette, so without this the only
     * difference between the Taaj build and the Meniura build is the name on
     * the header. The theme already knows how to take a seed and derive a
     * whole family from it WITH a 4.5:1 contrast guard, so a platform can be
     * recoloured from the admin panel without shipping a build to a single
     * driver's phone.
     *
     * Null — or anything that is not a valid hex — falls back to the
     * flavour's own colours, which is what every build does today.
     */
    val accentColor: String? = null,
    val privacyUrl: String? = null,
    val termsUrl: String? = null,
    val helpUrl: String? = null,
    val supportWhatsApp: String? = null,
    val supportEmail: String? = null,
) {
    /** The platform's name in the driver's language, or null if neither is set. */
    fun displayName(isArabic: Boolean): String? =
        (if (isArabic) nameAr else nameEn) ?: nameEn ?: nameAr
}
