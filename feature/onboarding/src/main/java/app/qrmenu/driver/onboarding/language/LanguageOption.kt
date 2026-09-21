package app.qrmenu.driver.onboarding.language

import app.qrmenu.driver.common.locale.SupportedLocales

/**
 * One row of the language picker.
 *
 * 🔴 [nativeName] is NOT a translated string resource, and must never become
 * one. A picker exists for a driver who cannot read the language the app is
 * currently showing: someone who reads only Bengali has to find "বাংলা"
 * whether the app happens to be in Arabic, English or Urdu right now. Putting
 * these names in `values-ar/strings.xml` would translate them along with
 * everything else and leave that driver with five words they cannot read.
 *
 * So each name is written once, in its own language, and shown unchanged in
 * every locale. Only the screen's title and its button are translated.
 *
 * [englishName] is the disambiguator for a driver who half-reads Latin script —
 * it is a constant too, for exactly the same reason.
 */
enum class LanguageOption(
    val code: String,
    val nativeName: String,
    val englishName: String,
) {
    Arabic("ar", "العربية", "Arabic"),
    English("en", "English", "English"),
    Urdu("ur", "اردو", "Urdu"),
    Bengali("bn", "বাংলা", "Bengali"),
    Hindi("hi", "हिन्दी", "Hindi"),
    ;

    /** Arabic and Urdu read right-to-left; a row renders in ITS OWN direction. */
    val isRtl: Boolean get() = SupportedLocales.isRtl(code)

    companion object {
        /**
         * The picker's rows, in the order [SupportedLocales] declares them —
         * so the list can never disagree with what the app actually supports.
         * A language added there without a row here fails [check] at startup of
         * the first test that touches this, which is the point.
         */
        val all: List<LanguageOption> = SupportedLocales.supported.map { code ->
            checkNotNull(entries.firstOrNull { it.code == code }) {
                "SupportedLocales declares '$code' but the picker has no row for it."
            }
        }
    }
}
