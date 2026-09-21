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

    // The section icons (Translate, PhotoSizeSelectLarge, BatteryStd,
    // Storefront-status glyphs, Logout) sit outside material3's small bundled
    // core set — same reason :feature:home and :feature:auth carry this.
    implementation(libs.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
