plugins {
    alias(libs.plugins.driver.android.feature)
}

android {
    namespace = "app.qrmenu.driver.updater"
}

dependencies {
    // AuthApi.appVersion() + AppVersionDto, and ClientUpdateGate — the
    // 426-triggered signal `UpdateRequiredInterceptor` raises — both live
    // here already.
    implementation(project(":core:network"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
