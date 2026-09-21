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
 * How large this driver wants the app drawn.
 *
 * 🔴 This REVERSES the design-system note that said per-app UI scale would not
 * be ported from the POS app because the system font scale covers it (decision
 * revised by the project owner, 2026-09-21). The system setting does cover
 * text — but it is buried several screens deep in Android's own settings, it
 * is global to every app on the phone, and a driver who finds this app's
 * default too large is not going to shrink their entire phone to fix it. The
 * control belongs where the complaint is.
 *
 * Unlike the POS app's per-terminal scale, this one also moves SPACING, not
 * just type: shrinking the letters inside cards built for larger ones leaves
 * a screen of half-empty boxes.
 *
 * Plain [SharedPreferences] for the same reason [AppLocaleStore] uses them —
 * it is read on the very first frame, before a coroutine could deliver it, and
 * a scale that arrives one frame late is a visible jump on every launch.
 */
@Singleton
class UiScaleStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _scale = MutableStateFlow(read())

    /** The live choice, for a settings screen to render and the theme to apply. */
    val scale: StateFlow<UiScale> = _scale.asStateFlow()

    /** Synchronous read for the very first composition. */
    fun current(): UiScale = _scale.value

    fun set(scale: UiScale) {
        prefs.edit().putString(KEY, scale.key).apply()
        _scale.value = scale
    }

    private fun read(): UiScale {
        val stored = prefs.getString(KEY, null) ?: return UiScale.Default
        return UiScale.fromKey(stored)
    }

    private companion object {
        const val FILE_NAME = "driver_ui_scale"
        const val KEY = "ui_scale"
    }
}

/**
 * The four steps offered. Deliberately four fixed steps rather than a
 * continuous slider: a driver adjusting this is standing beside a running car,
 * not tuning a design, and four labelled stops are reachable with one thumb in
 * one pass.
 *
 * [factor] multiplies BOTH the type scale and the spacing grid, so a card
 * shrinks as a whole instead of becoming a small sentence in a big box.
 */
enum class UiScale(val key: String, val factor: Float) {
    Small("small", 0.88f),
    Default("default", 1.0f),
    Large("large", 1.12f),
    Largest("largest", 1.28f),
    ;

    companion object {
        fun fromKey(key: String): UiScale = entries.firstOrNull { it.key == key } ?: Default
    }
}
