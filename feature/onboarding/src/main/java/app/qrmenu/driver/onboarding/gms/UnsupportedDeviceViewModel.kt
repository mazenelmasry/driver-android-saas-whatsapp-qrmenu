package app.qrmenu.driver.onboarding.gms

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reads Google Mobile Services presence once, at screen creation.
 *
 * Deliberately no loading/error/offline states (same reasoning as
 * `LanguageViewModel`): [Context.googleServicesPresence] is a local
 * [android.content.pm.PackageManager] lookup, not a network call.
 */
@HiltViewModel
class UnsupportedDeviceViewModel @Inject constructor(
    @ApplicationContext context: Context,
) : ViewModel() {

    val presence: GmsPresence = context.googleServicesPresence()

    val shouldWarn: Boolean = shouldWarnAboutMissingGoogleServices(presence)
}
