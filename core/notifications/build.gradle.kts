plugins {
    alias(libs.plugins.driver.android.library)
    alias(libs.plugins.driver.android.hilt)
}

android {
    namespace = "app.qrmenu.driver.alerts"
}

dependencies {
    api(project(":core:common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
