package app.qrmenu.driver.onboarding.language

import androidx.lifecycle.ViewModel
import app.qrmenu.driver.datastore.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The language picker's state.
 *
 * Deliberately has no loading, error or offline state: this screen reads and
 * writes local preferences only and never touches the network, so the app's
 * four-state rule (CLAUDE.md § قواعد الواجهة) has nothing to describe here.
 * The first screen that loads from the server — login — is where those states
 * become mandatory.
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val localeManager: LocaleManager,
) : ViewModel() {

    /**
     * What is currently ticked.
     *
     * Falls back to the app default only for the TICK: `null` in the store means
     * "not chosen yet" (decision 9), and this screen exists precisely because
     * that is different from having chosen Arabic. So the row is pre-highlighted
     * for orientation, while [hasChosen] stays false and the driver still has to
     * confirm.
     */
    private val _selected = MutableStateFlow(
        localeManager.language.value ?: localeManager.default,
    )
    val selected: StateFlow<String> = _selected.asStateFlow()

    /** True once a choice exists, so the picker is not shown again on next launch. */
    val hasChosen: StateFlow<Boolean> = localeManager.hasChosenLanguage

    /**
     * Highlights a row — and nothing else.
     *
     * 🔴 It deliberately does NOT write to the store. Writing on tap would make
     * a half-made choice permanent: a driver who taps a row to see what it says
     * and then closes the app would have "chosen" it, and the picker — whose
     * whole reason to exist is that `null` means "not chosen yet" (decision 9) —
     * would never appear again.
     *
     * The screen still previews the language live, by resolving its own strings
     * in [selected] rather than by changing the app's language. See
     * `LanguageScreen`.
     */
    fun select(code: String) {
        _selected.value = code
    }

    /**
     * Commits the choice and applies it app-wide.
     *
     * This is the only write. A driver who never tapped a row — and simply
     * pressed Continue on the pre-highlighted default — still leaves an EXPLICIT
     * choice behind, so the picker does not reappear for exactly the driver the
     * default suited.
     */
    fun confirm() {
        localeManager.set(_selected.value)
    }
}
