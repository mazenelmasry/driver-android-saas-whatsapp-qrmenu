plugins {
    alias(libs.plugins.driver.android.library)
    alias(libs.plugins.driver.android.hilt)
}

android {
    namespace = "app.qrmenu.driver.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
