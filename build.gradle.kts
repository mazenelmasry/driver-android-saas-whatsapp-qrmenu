// Top-level build file. Plugins are declared here and applied per module.
//
// Firebase: only `google-services` is declared — the driver app needs Firebase
// for Phone Auth (OTP) and FCM (dispatch offers). Crashlytics and Performance
// are deliberately NOT used: crash reporting is Sentry (CLAUDE.md § القرارات).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
