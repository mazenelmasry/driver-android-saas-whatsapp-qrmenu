plugins {
    alias(libs.plugins.driver.android.feature)
}

android {
    namespace = "app.qrmenu.driver.account"
}

dependencies {
    // TokenStore (sign-out) + LocaleManager + UiScaleStore all live here.
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))

    // NotificationHistoryDao (sign-out privacy purge) + DriverActionOutboxDao
    // (unsent-actions warning in the sign-out dialog).
    implementation(project(":core:database"))

    // The section icons (Translate, PhotoSizeSelectLarge, BatteryStd,
    // Storefront-status glyphs, Logout) sit outside material3's small bundled
    // core set — same reason :feature:home and :feature:auth carry this.
    implementation(libs.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
