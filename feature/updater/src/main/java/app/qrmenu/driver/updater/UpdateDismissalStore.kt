package app.qrmenu.driver.updater

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Which `latest_version_code` did the driver already close the optional-update
 * banner for?" — one integer, so the banner does not reappear every launch for
 * a build the driver has already chosen to keep ignoring, but DOES come back
 * the moment a newer `latest_version_code` ships (the earlier dismissal was
 * for a different, now-stale, release).
 *
 * Plain [SharedPreferences], same reasoning as `:core:datastore`'s
 * `UiScaleStore`: nothing here needs a coroutine-backed store, and this
 * module owns its own tiny file rather than reaching into `:core:datastore`
 * for a single int.
 */
@Singleton
class UpdateDismissalStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _dismissedVersionCode = MutableStateFlow(read())

    /** `null` = nothing dismissed yet. */
    val dismissedVersionCode: StateFlow<Int?> = _dismissedVersionCode.asStateFlow()

    fun dismiss(latestVersionCode: Int) {
        prefs.edit().putInt(KEY, latestVersionCode).apply()
        _dismissedVersionCode.value = latestVersionCode
    }

    private fun read(): Int? {
        val value = prefs.getInt(KEY, NOT_SET)
        return value.takeIf { it != NOT_SET }
    }

    private companion object {
        const val FILE_NAME = "driver_update_dismissal"
        const val KEY = "dismissed_latest_version_code"
        const val NOT_SET = -1
    }
}
