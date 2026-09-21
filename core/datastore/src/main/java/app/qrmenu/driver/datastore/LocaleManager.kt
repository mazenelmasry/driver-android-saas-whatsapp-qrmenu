package app.qrmenu.driver.datastore

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.qrmenu.driver.common.locale.SupportedLocales
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * Switches the in-app language at runtime via [AppCompatDelegate.setApplicationLocales],
 * which forwards to the framework's per-app language on Android 13+ and to an
 * AppCompat override below it.
 *
 * ## Two differences from the POS app's LocaleManager — both deliberate
 *
 * **1. Five languages, not two.** `ar · en · ur · bn · hi` (decision 8), with
 * Arabic and Urdu right-to-left. The set lives in [SupportedLocales] in
 * `:core:common` so the network and UI layers can ask "is this RTL?" without
 * depending on this module.
 *
 * **2. There is no second store.** POS keeps the language in DataStore
 * (`PreferencesRepo`) AND in a synchronous cache, and reconciles the two on
 * boot. Here [AppLocaleStore] is the only store: it is already durable
 * `SharedPreferences`, and it is the one `MainActivity.attachBaseContext(...)`
 * can read before Hilt exists. A second copy would only create the question of
 * which one is right.
 *
 * ## Latin numerals, always
 * Every locale set here carries the BCP-47 `nu-latn` extension, so `String.format`,
 * `stringResource("%d")`, `DecimalFormat` and the date/time pickers all emit
 * 0-9 even in Arabic or Urdu. A driver reads an address, a distance and a
 * cash-to-collect amount at a glance, often at speed or at night — Eastern
 * Arabic-Indic digits (٠-٩) are a genuine misread risk there, and a misread
 * amount is money.
 *
 * Call [restore] once from `DriverApplication.onCreate`.
 */
@Singleton
class LocaleManager @Inject constructor(
    private val appLocaleStore: AppLocaleStore,
) {
    val supported: List<String> = SupportedLocales.supported

    val default: String = SupportedLocales.default

    /** `null` until the driver picks a language — the picker screen watches this (decision 9). */
    val language: StateFlow<String?> = appLocaleStore.language

    /** `true` once an explicit choice exists, so the picker is not shown twice. */
    val hasChosenLanguage: StateFlow<Boolean> = appLocaleStore.hasChosenLanguage

    fun isRtl(language: String): Boolean = SupportedLocales.isRtl(language)

    /**
     * Records the driver's choice and applies it immediately.
     *
     * The store is written FIRST so that a `recreate()` in the same tick — which
     * is how the language picker hands over to the next screen — reads the new
     * value in `attachBaseContext`.
     */
    fun set(language: String) {
        require(SupportedLocales.isSupported(language)) { "Unsupported locale: $language" }

        appLocaleStore.set(language)
        AppCompatDelegate.setApplicationLocales(buildLocaleList(language))
    }

    /**
     * Applies the stored language on start.
     *
     * **Does not write anything when nothing was chosen.** Applying the default
     * here would look identical to an explicit choice of Arabic and the picker
     * would never appear again — the exact confusion [AppLocaleStore] is
     * nullable to avoid. An unchosen app simply runs in the default until the
     * picker is answered.
     */
    fun restore() {
        AppCompatDelegate.setApplicationLocales(
            buildLocaleList(appLocaleStore.effectiveLanguage(default)),
        )
    }

    private fun buildLocaleList(language: String): LocaleListCompat =
        LocaleListCompat.create(SupportedLocales.buildLatinLocale(language))
}
