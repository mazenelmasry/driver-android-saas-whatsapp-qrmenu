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
 * The driver's chosen UI language, stored in plain (non-secret) [SharedPreferences]
 * so it can be read **synchronously** — mirrors the POS app's `AppLocaleStore`
 * (`app.qrmenu.pos.datastore.AppLocaleStore`) for the same reason: this is the
 * store `MainActivity.attachBaseContext(...)` reads BEFORE `onCreate`/Hilt
 * injection can run a coroutine, so the per-app locale is applied to
 * [android.content.res.Configuration] on the very first frame.
 *
 * ## Why this store is nullable where POS's is not (CLAUDE.md decision #9)
 * POS always has a language — Arabic — the moment the app is installed; there
 * is no "language picker" screen. The driver app's decision #9 is the
 * opposite: **"شاشة اختيار اللغة أولاً" ثم العربية افتراضاً** — a first-run
 * language-picker screen must appear, and only AFTER the driver picks
 * (possibly Arabic itself) does the app commit to a language.
 *
 * If this store could not distinguish "never chosen" from "chose Arabic" —
 * e.g. by defaulting the persisted value to `"ar"` like POS does — the
 * picker screen could never tell the two apart: it would either show on
 * EVERY launch (an unset value always reads as "unset") or NEVER show again
 * after the very first process death that happened to read the SharedPreferences
 * default (an unset value defaulting to `"ar"` looks identical to an explicit
 * choice). [language] is therefore `String?`: `null` means "not chosen yet",
 * any of [app.qrmenu.driver.common.locale.SupportedLocales.supported] means
 * "the driver chose this, including possibly the default".
 *
 * [effectiveLanguage] is the one convenience read for every OTHER caller
 * (locale application at boot, formatters) that needs an unconditional
 * language and does not care whether it came from an explicit choice.
 */
@Singleton
class AppLocaleStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(prefs.getString(KEY_LANGUAGE, null))

    /** `null` = the driver has never picked a language yet (show the picker). */
    val language: StateFlow<String?> = _language.asStateFlow()

    private val _hasChosenLanguage = MutableStateFlow(_language.value != null)

    /** Convenience: `true` once the driver has made an explicit choice. */
    val hasChosenLanguage: StateFlow<Boolean> = _hasChosenLanguage.asStateFlow()

    /** The language to actually apply — [language] if chosen, else the app default. */
    fun effectiveLanguage(default: String): String = _language.value ?: default

    fun set(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
        _language.value = language
        _hasChosenLanguage.value = true
    }

    /** Test/debug hook: forgets the choice so the language picker reappears. */
    fun clear() {
        prefs.edit().remove(KEY_LANGUAGE).apply()
        _language.value = null
        _hasChosenLanguage.value = false
    }

    companion object {
        private const val FILE_NAME = "driver_app_locale"
        private const val KEY_LANGUAGE = "language"

        /**
         * Synchronous, Hilt-free read for `MainActivity.attachBaseContext(...)`,
         * which runs before the Hilt-injected [AppLocaleStore] instance exists.
         * Returns `null` when the driver has not chosen a language yet — the
         * caller decides the boot-time fallback (see [effectiveLanguage]).
         */
        fun readLanguage(context: Context): String? =
            context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, null)
    }
}
