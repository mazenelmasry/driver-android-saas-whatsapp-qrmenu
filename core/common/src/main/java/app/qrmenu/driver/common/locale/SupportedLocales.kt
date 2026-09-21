package app.qrmenu.driver.common.locale

import java.util.Locale

/**
 * Pure-JVM (no AndroidX dependency, unit-testable without Robolectric — same
 * precedent as the POS app's `LocaleManager.buildLatinLocale` companion) home
 * for the driver app's five-language matrix (CLAUDE.md decision #8).
 *
 * Kept in `:core:common` (rather than alongside [app.qrmenu.driver.datastore.LocaleManager]
 * in `:core:datastore`, as the POS app does) because the language *codes*,
 * the RTL set, and the Latin-numeral locale builder are pure data/logic with
 * zero Android-framework dependency — every module above `:core:common`
 * (network error messages, UI string direction, formatters) needs to ask
 * "is this language RTL?" or "build me the app's canonical locale for X"
 * without pulling in `:core:datastore`'s `AppCompatDelegate`/SharedPreferences
 * dependency chain.
 */
object SupportedLocales {

    /** CLAUDE.md decision #8 — ar Arabic, en English, ur Urdu, bn Bengali, hi Hindi. */
    val supported: List<String> = listOf("ar", "en", "ur", "bn", "hi")

    /** CLAUDE.md decision #9 — a language-picker screen decides this; the app never guesses. */
    const val default: String = "ar"

    /** CLAUDE.md § design system — Arabic and Urdu render right-to-left; the rest left-to-right. */
    private val rtl: Set<String> = setOf("ar", "ur")

    fun isSupported(language: String): Boolean = language in supported

    fun isRtl(language: String): Boolean = language in rtl

    /**
     * Builds a [Locale] pinned to Latin numerals via the `nu-latn` Unicode BCP-47
     * extension, so every digit-rendering path (`String.format`, `DecimalFormat`,
     * `stringResource`, date/time pickers) emits Western digits (0-9) even on the
     * Arabic or Urdu locale.
     *
     * A driver reads an address, a distance, and a cash-to-collect amount at a
     * glance, often at speed or at night — Eastern Arabic-Indic digits (٠-٩) are
     * a real misread risk there, exactly as they were for the POS cashier
     * reconciling against Latin-digit price tags (see the POS `LocaleManager`
     * doc this mirrors). CLAUDE.md § design system: "الأرقام لاتينية دائماً".
     */
    fun buildLatinLocale(language: String): Locale =
        Locale.Builder()
            .setLanguage(language)
            .setUnicodeLocaleKeyword("nu", "latn")
            .build()
}
