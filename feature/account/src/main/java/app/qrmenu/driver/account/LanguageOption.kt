package app.qrmenu.driver.account

import app.qrmenu.driver.common.locale.SupportedLocales

/**
 * One row of the in-app language switcher.
 *
 * 🔴 [nativeName] is NOT a translated string resource, and must never become
 * one — mirrors `:feature:onboarding`'s `LanguageOption` (the first-run
 * picker) for the exact same reason: a driver who reads only Bengali has to
 * find "বাংলা" whether the app happens to be in Arabic, English or Urdu right
 * now. Duplicated here rather than imported from `:feature:onboarding`
 * because a settings row and a first-run wizard step are different screens in
 * different modules with no shared lifecycle — the thing that must never
 * drift is the WRITE path ([app.qrmenu.driver.datastore.LocaleManager.set]),
 * not this presentation-only row list, and both screens call the same one.
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
         */
        val all: List<LanguageOption> = SupportedLocales.supported.map { code ->
            checkNotNull(entries.firstOrNull { it.code == code }) {
                "SupportedLocales declares '$code' but the picker has no row for it."
            }
        }

        fun forCode(code: String): LanguageOption = all.firstOrNull { it.code == code } ?: Arabic
    }
}
