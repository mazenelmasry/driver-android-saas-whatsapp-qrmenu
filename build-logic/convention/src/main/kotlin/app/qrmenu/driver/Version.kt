package app.qrmenu.driver

/**
 * The app version — ONE definition, read by the `app` module's
 * `versionCode`/`versionName` AND by every module's BuildConfig.
 *
 * It lives here rather than in `app/build.gradle.kts` because the self-updater
 * (CLAUDE.md § التوزيع والتحديث الذاتى) compares what the CLIENT reports against
 * `min_version_code` from the server. If the number the installer stamps and the
 * number the client reports could ever differ, the app would either lock out a
 * driver who is already up to date or let an outdated one keep working — and
 * both failures are silent.
 *
 * Format (frozen, CLAUDE.md § ترقيم الإصدارات):
 *   versionName = MAJOR.MINOR.PATCH
 *   versionCode = MAJOR*10000 + MINOR*100 + PATCH
 * The comparison is ALWAYS on the integer: as a string, "1.10.0" sorts before
 * "1.9.0". [CODE] never goes backwards, even if a feature is reverted.
 */
object DriverVersion {
    const val MAJOR = 1
    const val MINOR = 0
    const val PATCH = 0

    const val CODE: Int = MAJOR * 10_000 + MINOR * 100 + PATCH
    const val NAME: String = "$MAJOR.$MINOR.$PATCH"
}
