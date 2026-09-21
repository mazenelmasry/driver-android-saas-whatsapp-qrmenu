package app.qrmenu.driver.auth.login

import android.content.Context
import android.telephony.TelephonyManager
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import java.util.Locale

/**
 * One country in the dialling-code selector.
 *
 * The list is NOT a hand-kept table. [name] comes from the platform's own
 * localised country names and [dialCode] from libphonenumber — the same library
 * the backend canonicalises with — so the app cannot drift from either the
 * device's language or the server's idea of a valid number. A hardcoded table
 * would need translating five times and would go stale.
 */
data class DialingRegion(
    /** ISO 3166-1 alpha-2, e.g. `SA`. */
    val code: String,
    /** Localised country name, e.g. "السعودية" / "Saudi Arabia". */
    val name: String,
    /** International dialling code WITHOUT the plus, e.g. `966`. */
    val dialCode: Int,
) {
    val displayDialCode: String get() = "+$dialCode"
}

/**
 * Builds and detects the dialling region.
 *
 * ## Why it is detected, not asked
 * A driver typing their own number should not also have to find their country in
 * a list of two hundred. The SIM knows, and it is right far more often than the
 * phone's language is: a Bengali-speaking driver working in Riyadh has a Saudi
 * SIM and an app in Bengali, and the phone's LANGUAGE would have guessed
 * Bangladesh and silently built the wrong number.
 *
 * Order: SIM country, then the network the phone is registered on, then the
 * locale's country as a last resort. It is still a SELECTOR — a detected value
 * is a good default, not a decision the driver cannot overrule.
 */
class DialingRegions(private val phoneNumberUtil: PhoneNumberUtil) {

    /** Every region the library knows a dialling code for, named in [locale], sorted by that name. */
    fun all(locale: Locale): List<DialingRegion> =
        phoneNumberUtil.supportedRegions
            .mapNotNull { region ->
                val dialCode = phoneNumberUtil.getCountryCodeForRegion(region)
                if (dialCode == 0) return@mapNotNull null
                DialingRegion(
                    code = region,
                    name = Locale.Builder().setRegion(region).build().getDisplayCountry(locale)
                        .takeIf { it.isNotBlank() && it != region }
                        ?: region,
                    dialCode = dialCode,
                )
            }
            .sortedWith(compareBy(java.text.Collator.getInstance(locale)) { it.name })

    /**
     * The best guess for this handset, or null when nothing is knowable — a
     * tablet with no SIM and a locale with no country. The caller decides the
     * fallback; this does not invent one, because a wrong country silently
     * builds a wrong phone number and the driver never sees why login failed.
     */
    fun detect(context: Context, locale: Locale): DialingRegion? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        val candidates = listOfNotNull(
            // The SIM's home country: what the number actually belongs to.
            telephony?.simCountryIso,
            // Where the phone is registered right now — right for a driver
            // roaming on a local network with a foreign SIM.
            telephony?.networkCountryIso,
            locale.country,
        )

        for (candidate in candidates) {
            val region = candidate.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: continue
            val dialCode = phoneNumberUtil.getCountryCodeForRegion(region)
            if (dialCode != 0) {
                return DialingRegion(
                    code = region,
                    name = Locale.Builder().setRegion(region).build().getDisplayCountry(locale),
                    dialCode = dialCode,
                )
            }
        }
        return null
    }

    /**
     * Joins the selected region and what the driver typed into the E.164 form
     * the API expects, or null when the pair is not a valid number.
     *
     * Validation is the library's, per region — so a nine-digit Saudi mobile and
     * a ten-digit Egyptian one are both judged by their own rules instead of by
     * a length the app made up. A leading zero is dropped by the parser, which
     * is what a driver reading "0501234567" off a card will type.
     */
    fun toE164(region: DialingRegion, nationalNumber: String): String? = runCatching {
        val parsed = phoneNumberUtil.parse(nationalNumber, region.code)
        if (!phoneNumberUtil.isValidNumber(parsed)) return null
        phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
    }.getOrNull()
}
